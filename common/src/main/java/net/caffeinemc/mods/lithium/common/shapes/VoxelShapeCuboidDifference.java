package net.caffeinemc.mods.lithium.common.shapes;

import com.mojang.math.OctahedralGroup;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A {@link VoxelShape} that represents a cuboid minus a list of cuboids without storing any voxels. It is used to
 * replace the very expensive incremental {@link Shapes#joinUnoptimized} / {@link Shapes#joinIsNotEmpty} calls jigsaw
 * structure placement uses to track the remaining free space (structure bounds minus already placed pieces).
 * <p>
 * Instead of merging coordinate lists and joining voxel sets, subtracting a placed piece just appends 6 doubles to a
 * list, and testing whether a candidate piece exceeds the free space is a linear scan over the subtracted cuboids
 * using exact double comparisons.
 * <p>
 * Exactness: All coordinates handled by the fast paths are required to be aligned to a 0.25 grid (jigsaw placement
 * only produces integer coordinates and integer coordinates deflated by 0.25). Distinct coordinates on this grid
 * differ by at least 0.25, so vanilla's 1e-7 epsilon coordinate merging in
 * {@link net.minecraft.world.phys.shapes.IndirectMerger} never merges distinct coordinates and never produces cells
 * with a volume close to 0. Therefore vanilla's voxel-based join results are exactly the results of unrounded box
 * arithmetic, which this class implements. Inputs that are not grid aligned are rejected by the factory methods and
 * the fast path queries, in which case callers fall back to vanilla behavior on the {@link #materialized()} shape.
 * <p>
 * Vanilla parity of the shape itself: Any {@link VoxelShape} method invoked on this shape delegates to
 * {@link #materialized()}, which lazily computes the exact shape vanilla would have built by replaying the skipped
 * {@link Shapes#create(AABB)} and {@link Shapes#joinUnoptimized} calls. The {@link DiscreteVoxelShape} of this shape
 * delegates in the same way, so even code that directly accesses the {@link VoxelShape#shape} field behaves as if
 * the vanilla shape was stored here.
 */
public class VoxelShapeCuboidDifference extends VoxelShape {
    /**
     * Maximum number of cuboids extracted when converting an unknown free space shape in
     * {@link #tryConvert(VoxelShape)}. Vanilla's initial jigsaw free space converts to a single cuboid. The limit only
     * exists to avoid degrading modded shapes with many cuboids into slow linear scans.
     */
    private static final int MAX_CONVERTED_CUBOIDS = 64;

    /**
     * Return values of {@link #exceedsFreeSpace(VoxelShape)}.
     */
    public static final int FALLBACK = -1;
    public static final int CONTAINED = 0;
    public static final int EXCEEDS = 1;

    /**
     * The shape this instance was converted from, used as the starting point when replaying the subtractions in
     * {@link #materialized()}. If null, the starting point is {@link Shapes#create(AABB)} of the container cuboid,
     * exactly like the call this shape was created in place of.
     */
    @Nullable
    private final VoxelShape initialShape;

    private final double minX, minY, minZ, maxX, maxY, maxZ;

    /**
     * The subtracted cuboids, 6 doubles (minX, minY, minZ, maxX, maxY, maxZ) per cuboid. The list is shared between
     * instances created by {@link #subtract}: an instance only reads the first {@link #cuboidCount} * 6 entries and
     * appends in place when it is the instance the list was last appended for.
     */
    private final DoubleArrayList cuboids;

    /**
     * Number of leading cuboids that describe the difference between the container cuboid and {@link #initialShape}.
     * These only exist for containment queries and must not be replayed in {@link #materialized()}.
     */
    private final int initialCuboidCount;
    private final int cuboidCount;

    @Nullable
    private VoxelShape materialized;

    private VoxelShapeCuboidDifference(MaterializingDiscreteVoxelShape voxels, @Nullable VoxelShape initialShape,
                                       double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                       DoubleArrayList cuboids, int initialCuboidCount, int cuboidCount) {
        super(voxels);
        voxels.owner = this;
        this.initialShape = initialShape;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.cuboids = cuboids;
        this.initialCuboidCount = initialCuboidCount;
        this.cuboidCount = cuboidCount;
    }

    /**
     * Creates a free space shape equivalent to {@link Shapes#create(AABB)} of the given cuboid, or null if the
     * coordinates are not grid aligned.
     */
    @Nullable
    public static VoxelShapeCuboidDifference tryCreate(AABB cuboid) {
        if (!isAligned(cuboid.minX, cuboid.minY, cuboid.minZ, cuboid.maxX, cuboid.maxY, cuboid.maxZ)) {
            return null;
        }
        return new VoxelShapeCuboidDifference(new MaterializingDiscreteVoxelShape(), null,
                cuboid.minX, cuboid.minY, cuboid.minZ, cuboid.maxX, cuboid.maxY, cuboid.maxZ,
                new DoubleArrayList(), 0, 0);
    }

    /**
     * Converts an existing free space shape into the cuboid difference representation by computing the difference
     * between the shape's bounding box and the shape. Returns the given shape unchanged if it cannot be represented
     * exactly, in which case all queries keep using vanilla code.
     */
    public static VoxelShape tryConvert(VoxelShape freeSpace) {
        if (freeSpace instanceof VoxelShapeCuboidDifference || freeSpace.isEmpty() || !isAligned(freeSpace)) {
            return freeSpace;
        }
        AABB bounds = freeSpace.bounds();
        VoxelShape complement = Shapes.joinUnoptimized(Shapes.create(bounds), freeSpace, BooleanOp.ONLY_FIRST);
        List<AABB> complementCuboids = complement.toAabbs();
        if (complementCuboids.size() > MAX_CONVERTED_CUBOIDS) {
            return freeSpace;
        }
        DoubleArrayList cuboids = new DoubleArrayList(complementCuboids.size() * 6);
        for (AABB cuboid : complementCuboids) {
            cuboids.add(cuboid.minX);
            cuboids.add(cuboid.minY);
            cuboids.add(cuboid.minZ);
            cuboids.add(cuboid.maxX);
            cuboids.add(cuboid.maxY);
            cuboids.add(cuboid.maxZ);
        }
        return new VoxelShapeCuboidDifference(new MaterializingDiscreteVoxelShape(), freeSpace,
                bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ,
                cuboids, complementCuboids.size(), complementCuboids.size());
    }

    /**
     * Returns the vanilla shape to use when a call site cannot take a fast path. This must be used instead of passing
     * this shape to vanilla shape operations directly.
     */
    public static VoxelShape unwrap(VoxelShape shape) {
        if (shape instanceof VoxelShapeCuboidDifference cuboidDifference) {
            return cuboidDifference.materialized();
        }
        return shape;
    }

    /**
     * Fast path for {@code Shapes.joinIsNotEmpty(this, cuboid, BooleanOp.ONLY_SECOND)}: whether any part of the given
     * cuboid shape lies outside this free space.
     *
     * @return {@link #EXCEEDS} if the cuboid exceeds the free space, {@link #CONTAINED} if not, {@link #FALLBACK} if
     * the query cannot be answered exactly and the caller must fall back to vanilla behavior using
     * {@link #unwrap(VoxelShape)}
     */
    public int exceedsFreeSpace(VoxelShape cuboid) {
        DoubleList xCoords = cuboid.getCoords(Direction.Axis.X);
        DoubleList yCoords = cuboid.getCoords(Direction.Axis.Y);
        DoubleList zCoords = cuboid.getCoords(Direction.Axis.Z);
        if (xCoords.size() != 2 || yCoords.size() != 2 || zCoords.size() != 2 || cuboid.isEmpty()) {
            return FALLBACK;
        }
        double minX = xCoords.getDouble(0);
        double minY = yCoords.getDouble(0);
        double minZ = zCoords.getDouble(0);
        double maxX = xCoords.getDouble(1);
        double maxY = yCoords.getDouble(1);
        double maxZ = zCoords.getDouble(1);
        if (!(minX < maxX) || !(minY < maxY) || !(minZ < maxZ) || !isAligned(minX, minY, minZ, maxX, maxY, maxZ)) {
            return FALLBACK;
        }
        return this.fullyContains(minX, minY, minZ, maxX, maxY, maxZ) ? CONTAINED : EXCEEDS;
    }

    /**
     * Fast path for {@code Shapes.joinUnoptimized(this, cuboid, BooleanOp.ONLY_FIRST)}: the free space minus the
     * given cuboid shape.
     *
     * @return the new free space shape, or null if the caller must fall back to vanilla behavior using
     * {@link #unwrap(VoxelShape)}
     */
    @Nullable
    public VoxelShape trySubtract(VoxelShape cuboid) {
        DoubleList xCoords = cuboid.getCoords(Direction.Axis.X);
        DoubleList yCoords = cuboid.getCoords(Direction.Axis.Y);
        DoubleList zCoords = cuboid.getCoords(Direction.Axis.Z);
        if (xCoords.size() != 2 || yCoords.size() != 2 || zCoords.size() != 2 || cuboid.isEmpty()) {
            return null;
        }
        double minX = xCoords.getDouble(0);
        double minY = yCoords.getDouble(0);
        double minZ = zCoords.getDouble(0);
        double maxX = xCoords.getDouble(1);
        double maxY = yCoords.getDouble(1);
        double maxZ = zCoords.getDouble(1);
        if (!(minX < maxX) || !(minY < maxY) || !(minZ < maxZ) || !isAligned(minX, minY, minZ, maxX, maxY, maxZ)) {
            return null;
        }
        return this.subtract(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean fullyContains(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (minX < this.minX || minY < this.minY || minZ < this.minZ ||
                maxX > this.maxX || maxY > this.maxY || maxZ > this.maxZ) {
            return false;
        }
        double[] cuboids = this.cuboids.elements();
        for (int i = 0, end = this.cuboidCount * 6; i < end; i += 6) {
            if (minX < cuboids[i + 3] && cuboids[i] < maxX &&
                    minY < cuboids[i + 4] && cuboids[i + 1] < maxY &&
                    minZ < cuboids[i + 5] && cuboids[i + 2] < maxZ) {
                return false;
            }
        }
        return true;
    }

    private VoxelShapeCuboidDifference subtract(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        DoubleArrayList cuboids = this.cuboids;
        int numEntries = this.cuboidCount * 6;
        if (cuboids.size() != numEntries) {
            //the list was already appended for another instance, diverge with a copy
            DoubleArrayList copy = new DoubleArrayList(numEntries + 6);
            copy.addElements(0, cuboids.elements(), 0, numEntries);
            cuboids = copy;
        }
        cuboids.add(minX);
        cuboids.add(minY);
        cuboids.add(minZ);
        cuboids.add(maxX);
        cuboids.add(maxY);
        cuboids.add(maxZ);
        return new VoxelShapeCuboidDifference(new MaterializingDiscreteVoxelShape(), this.initialShape,
                this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ,
                cuboids, this.initialCuboidCount, this.cuboidCount + 1);
    }

    private static boolean isAligned(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return isAligned(minX) && isAligned(minY) && isAligned(minZ) &&
                isAligned(maxX) && isAligned(maxY) && isAligned(maxZ);
    }

    private static boolean isAligned(VoxelShape shape) {
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            DoubleList coords = shape.getCoords(axis);
            for (int i = 0, size = coords.size(); i < size; i++) {
                if (!isAligned(coords.getDouble(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAligned(double coord) {
        //multiple of 0.25 and small enough that all box arithmetic on the 0.25 grid is exact
        double scaled = coord * 4.0;
        return scaled == Math.rint(scaled) && Math.abs(coord) <= 1.0E9;
    }

    /**
     * The shape vanilla would have stored instead of this one, computed by replaying the skipped shape operations.
     */
    public VoxelShape materialized() {
        VoxelShape materialized = this.materialized;
        if (materialized == null) {
            materialized = this.initialShape != null ? this.initialShape :
                    Shapes.create(new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ));
            double[] cuboids = this.cuboids.elements();
            for (int i = this.initialCuboidCount * 6, end = this.cuboidCount * 6; i < end; i += 6) {
                AABB cuboid = new AABB(cuboids[i], cuboids[i + 1], cuboids[i + 2], cuboids[i + 3], cuboids[i + 4], cuboids[i + 5]);
                materialized = Shapes.joinUnoptimized(materialized, Shapes.create(cuboid), BooleanOp.ONLY_FIRST);
            }
            this.materialized = materialized;
        }
        return materialized;
    }

    //Delegate the VoxelShape API to the materialized vanilla shape. Vanilla jigsaw placement never calls any
    //of these, but mods interacting with the free space shape must observe vanilla behavior.
    //The protected methods (get, findIndex, isCubeLike, collideX) cannot delegate to another instance, but their
    //inherited implementations only read this.getCoords(...) and this.shape, which already delegate. The materialized
    //shape is always an ArrayVoxelShape or Shapes.empty(), which use those same inherited implementations, so the
    //behavior is identical without overriding them.

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return this.materialized().getCoords(axis);
    }

    @Override
    public double min(Direction.Axis axis) {
        return this.materialized().min(axis);
    }

    @Override
    public double max(Direction.Axis axis) {
        return this.materialized().max(axis);
    }

    @Override
    public AABB bounds() {
        return this.materialized().bounds();
    }

    @Override
    public VoxelShape singleEncompassing() {
        return this.materialized().singleEncompassing();
    }

    @Override
    public boolean isEmpty() {
        return this.materialized().isEmpty();
    }

    @Override
    public VoxelShape move(double x, double y, double z) {
        return this.materialized().move(x, y, z);
    }

    @Override
    public VoxelShape optimize() {
        return this.materialized().optimize();
    }

    @Override
    public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
        this.materialized().forAllEdges(consumer);
    }

    @Override
    public void forAllBoxes(Shapes.DoubleLineConsumer consumer) {
        this.materialized().forAllBoxes(consumer);
    }

    @Override
    public List<AABB> toAabbs() {
        return this.materialized().toAabbs();
    }

    @Override
    public double min(Direction.Axis axis, double primaryPosition, double secondaryPosition) {
        return this.materialized().min(axis, primaryPosition, secondaryPosition);
    }

    @Override
    public double max(Direction.Axis axis, double primaryPosition, double secondaryPosition) {
        return this.materialized().max(axis, primaryPosition, secondaryPosition);
    }

    @Override
    public BlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos) {
        return this.materialized().clip(from, to, pos);
    }

    @Override
    public Optional<Vec3> closestPointTo(Vec3 point) {
        return this.materialized().closestPointTo(point);
    }

    @Override
    public VoxelShape getFaceShape(Direction direction) {
        return this.materialized().getFaceShape(direction);
    }

    @Override
    public double collide(Direction.Axis axis, AABB box, double maxDist) {
        return this.materialized().collide(axis, box, maxDist);
    }

    @Override
    public boolean equals(Object other) {
        return this.materialized().equals(other);
    }

    @Override
    public String toString() {
        return this.materialized().toString();
    }

    /**
     * Placeholder voxel set that delegates to the materialized vanilla shape's voxel set, for code that accesses the
     * {@link VoxelShape#shape} field of the free space shape directly.
     */
    private static class MaterializingDiscreteVoxelShape extends DiscreteVoxelShape {
        private VoxelShapeCuboidDifference owner;

        MaterializingDiscreteVoxelShape() {
            super(0, 0, 0);
        }

        private DiscreteVoxelShape delegate() {
            return this.owner.materialized().shape;
        }

        @Override
        public DiscreteVoxelShape rotate(OctahedralGroup rotation) {
            return this.delegate().rotate(rotation);
        }

        @Override
        public boolean isFullWide(AxisCycle cycle, int x, int y, int z) {
            return this.delegate().isFullWide(cycle, x, y, z);
        }

        @Override
        public boolean isFullWide(int x, int y, int z) {
            return this.delegate().isFullWide(x, y, z);
        }

        @Override
        public boolean isFull(AxisCycle cycle, int x, int y, int z) {
            return this.delegate().isFull(cycle, x, y, z);
        }

        @Override
        public boolean isFull(int x, int y, int z) {
            return this.delegate().isFull(x, y, z);
        }

        @Override
        public void fill(int x, int y, int z) {
            this.delegate().fill(x, y, z);
        }

        @Override
        public boolean isEmpty() {
            return this.delegate().isEmpty();
        }

        @Override
        public int firstFull(Direction.Axis axis) {
            return this.delegate().firstFull(axis);
        }

        @Override
        public int lastFull(Direction.Axis axis) {
            return this.delegate().lastFull(axis);
        }

        @Override
        public int firstFull(Direction.Axis axis, int secondaryPosition, int tertiaryPosition) {
            return this.delegate().firstFull(axis, secondaryPosition, tertiaryPosition);
        }

        @Override
        public int lastFull(Direction.Axis axis, int secondaryPosition, int tertiaryPosition) {
            return this.delegate().lastFull(axis, secondaryPosition, tertiaryPosition);
        }

        @Override
        public int getSize(Direction.Axis axis) {
            return this.delegate().getSize(axis);
        }

        @Override
        public int getXSize() {
            return this.delegate().getXSize();
        }

        @Override
        public int getYSize() {
            return this.delegate().getYSize();
        }

        @Override
        public int getZSize() {
            return this.delegate().getZSize();
        }

        @Override
        public void forAllEdges(DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
            this.delegate().forAllEdges(consumer, combine);
        }

        @Override
        public void forAllBoxes(DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
            this.delegate().forAllBoxes(consumer, combine);
        }

        @Override
        public void forAllFaces(DiscreteVoxelShape.IntFaceConsumer consumer) {
            this.delegate().forAllFaces(consumer);
        }
    }
}
