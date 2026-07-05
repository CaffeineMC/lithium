package net.caffeinemc.mods.lithium.common.shapes;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * An efficient implementation of {@link VoxelShape} for a shape with one simple cuboid. Since there are only ever two
 * vertices in a single cuboid (the start and end points), we can eliminate needing to iterate over voxels and to find
 * vertices through using simple comparison logic to pick between either the start or end point.
 * <p>
 * Additionally, the function responsible for determining shape maxMovement has been simplified and optimized by taking
 * advantage of the fact that there is only ever one voxel in a simple cuboid shape, greatly speeding up collision
 * handling in most cases as block shapes are often nothing more than a single cuboid.
 */
public class VoxelShapeSimpleCube extends VoxelShape implements VoxelShapeCaster {
    static final double EPSILON = 1.0E-7D;

    final double minX, minY, minZ, maxX, maxY, maxZ;
    public final boolean isTiny;

    public VoxelShapeSimpleCube(DiscreteVoxelShape voxels, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        super(voxels);

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;

        this.isTiny =
                this.minX + 3 * EPSILON >= this.maxX ||
                        this.minY + 3 * EPSILON >= this.maxY ||
                        this.minZ + 3 * EPSILON >= this.maxZ;
    }

    @Override
    public VoxelShape move(double x, double y, double z) {
        return new VoxelShapeSimpleCube(this.shape, this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    @Override
    public double collideX(AxisCycle cycleDirection, AABB moving, double maxDist) {
        if (Math.abs(maxDist) < EPSILON) {
            return 0.0D;
        }

        return switch (cycleDirection) {
            case NONE ->
                    limitMovement(maxDist, this.minX, this.maxX, moving.minX, moving.maxX, this.minY, moving.maxY, moving.minY, this.maxY, this.minZ, moving.maxZ, moving.minZ, this.maxZ);
            case FORWARD ->
                    limitMovement(maxDist, this.minZ, this.maxZ, moving.minZ, moving.maxZ, this.minX, moving.maxX, moving.minX, this.maxX, this.minY, moving.maxY, moving.minY, this.maxY);
            case BACKWARD ->
                    limitMovement(maxDist, this.minY, this.maxY, moving.minY, moving.maxY, this.minZ, moving.maxZ, moving.minZ, this.maxZ, this.minX, moving.maxX, moving.minX, this.maxX);
        };
    }

    private static double limitMovement(double maxDist, double sMinA, double sMaxA, double bMinA, double bMaxA, double sMinB, double bMaxB, double bMinB, double sMaxB, double sMinC, double bMaxC, double bMinC, double sMaxC) {
        double maxMovement = VoxelShapeSimpleCube.limitMovement(sMinA, sMaxA, bMinA, bMaxA, maxDist);
        if ((maxMovement != maxDist) && hasOverlap(sMinB, sMaxB, bMinB, bMaxB) && hasOverlap(sMinC, sMaxC, bMinC, bMaxC)) {
            return maxMovement;
        }
        return maxDist;
    }

    static boolean hasOverlap(double sMin, double sMax, double bMin, double bMax) {
        return lessThan(sMin, bMax) && lessThan(bMin, sMax);
    }

    private static double limitMovement(double a1, double a2, double b1, double b2, double maxDist) {
        double maxMovement;

        if (maxDist > 0.0D) {
            maxMovement = a1 - b2;

            if ((maxMovement < -EPSILON) || (maxDist < maxMovement)) {
                //already far enough inside this shape to not collide with the surface or
                //outside the shape and still far enough away for no collision at all
                return maxDist;
            }
            //allow moving up to the shape but not into it. This also includes going backwards by at most EPSILON.
        } else {
            //whole code again, just negated for the other direction
            maxMovement = a2 - b1;

            if ((maxMovement > EPSILON) || (maxDist > maxMovement)) {
                return maxDist;
            }
        }

        return maxMovement;
    }

    @Override
    public List<AABB> toAabbs() {
        return Lists.newArrayList(this.bounds());
    }

    @Override
    public AABB bounds() {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    @Override
    public double min(Direction.Axis axis) {
        return axis.choose(this.minX, this.minY, this.minZ);
    }

    @Override
    public double max(Direction.Axis axis) {
        return axis.choose(this.maxX, this.maxY, this.maxZ);
    }

    @Override
    protected double get(Direction.Axis axis, int index) {
        if ((index < 0) || (index > 1)) {
            throw new ArrayIndexOutOfBoundsException();
        }

        return switch (axis) {
            case X -> (index == 0) ? this.minX : this.maxX;
            case Y -> (index == 0) ? this.minY : this.maxY;
            case Z -> (index == 0) ? this.minZ : this.maxZ;
        };

    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return switch (axis) {
            case X -> DoubleArrayList.wrap(new double[] { this.minX, this.maxX });
            case Y -> DoubleArrayList.wrap(new double[] { this.minY, this.maxY });
            case Z -> DoubleArrayList.wrap(new double[] { this.minZ, this.maxZ });
        };

    }


    @Override
    public boolean isEmpty() {
        return (this.minX >= this.maxX) || (this.minY >= this.maxY) || (this.minZ >= this.maxZ);
    }

    @Override
    protected int findIndex(Direction.Axis axis, double coord) {
        if (coord < this.min(axis)) {
            return -1;
        }

        if (coord >= this.max(axis)) {
            return 1;
        }

        return 0;
    }

    static boolean lessThan(double a, double b) {
        return (a + EPSILON) < b;
    }

    @Override
    public boolean intersectsJNE(AABB box, double blockX, double blockY, double blockZ) {
        return ((box.minX + 1e-7) < (this.maxX + blockX)) && ((box.maxX - 1e-7) > (this.minX + blockX)) &&
                ((box.minY + 1e-7) < (this.maxY + blockY)) && ((box.maxY - 1e-7) > (this.minY + blockY)) &&
                ((box.minZ + 1e-7) < (this.maxZ + blockZ)) && ((box.maxZ - 1e-7) > (this.minZ + blockZ));
    }


    @Override
    public void forAllBoxes(Shapes.DoubleLineConsumer boxConsumer) {
        boxConsumer.consume(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }
}
