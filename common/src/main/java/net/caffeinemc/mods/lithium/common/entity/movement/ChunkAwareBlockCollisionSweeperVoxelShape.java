package net.caffeinemc.mods.lithium.common.entity.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * ChunkAwareBlockCollisionSweeperVoxelShape iterates over blocks in one chunk section at a time. Together with the chunk
 * section keeping track of the amount of oversized blocks inside the number of iterations can often be reduced.
 * <p>
 * Since VoxelShape collisions are not fully associative, this collision sweeper ensures the collision at the
 * greatest position (lexicographically by z, then y, then x) is returned last.
 * <p>
 * This class also implements Iterator<VoxelShape>. Note that the iterator position can be reset using {@link ChunkAwareBlockCollisionSweeperVoxelShape#setCursorToStart()}.
 * Any previous iterator calls are also reset after calling that method.
 */
public class ChunkAwareBlockCollisionSweeperVoxelShape extends ChunkAwareBlockCollisionSweeper<VoxelShape> implements Iterable<VoxelShape> {

    private final boolean hideMaxCollision;
    private int maxHitX;
    private int maxHitY;
    private int maxHitZ;
    private VoxelShape maxShape;

    private int elementsStored;
    private VoxelShape[] storageShapes;
    private long[] storagePositions; //Contains packed block position when the VoxelShape needs to be moved, otherwise 0

    //Fields for iterator
    private int cursor = 0;

    //Intermediary single element storage used when storeResults is false, since the element must go somewhere in between hasNext() and next() invocations
    private VoxelShape currentElement; //If not null: this.pos holds the block pos of the shape

    public ChunkAwareBlockCollisionSweeperVoxelShape(Level world, @Nullable Entity entity, AABB box) {
        this(world, entity, box, false, false);
    }

    public ChunkAwareBlockCollisionSweeperVoxelShape(Level world, @Nullable Entity entity, AABB box, boolean hideMaxCollision, boolean storeResults) {
        super(world, entity, box);

        this.maxHitX = Integer.MIN_VALUE;
        this.maxHitY = Integer.MIN_VALUE;
        this.maxHitZ = Integer.MIN_VALUE;
        this.maxShape = null;
        this.hideMaxCollision = hideMaxCollision;

        if (storeResults) {
            int startSize = 0;
            this.storageShapes = new VoxelShape[startSize];
            this.storagePositions = new long[startSize];
        } else {
            this.storageShapes = null;
            this.storagePositions = null;
        }
        this.elementsStored = 0;
    }

    private void ensureStorageSize(int minSize) {
        if (this.storageShapes == null) {
            return;
        }
        if (minSize >= this.storageShapes.length) {
            minSize = Math.max(16, minSize);
            //TODO consider avoiding allocation using thread-local reusable arrays
            int newLength = Math.max(Math.min(Integer.MAX_VALUE - 8, this.storageShapes.length * 2), minSize);
            this.storageShapes = Arrays.copyOf(this.storageShapes, newLength);
            this.storagePositions = Arrays.copyOf(this.storagePositions, newLength);
        }
    }

    public VoxelShape getMaxCollision() {
        if (this.maxShape == null) {
            return null;
        }
        return this.maxShape.move(this.maxHitX, this.maxHitY, this.maxHitZ);
    }

    @Override
    protected void reset() {
        super.reset();
        this.maxHitX = Integer.MIN_VALUE;
        this.maxHitY = Integer.MIN_VALUE;
        this.maxHitZ = Integer.MIN_VALUE;
        this.maxShape = null;

        if (this.storageShapes != null) {
            int startSize = 0;
            this.storageShapes = new VoxelShape[startSize];
            this.storagePositions = new long[startSize];
        } else {
            //noinspection DataFlowIssue
            this.storageShapes = null;
            this.storagePositions = null;
        }
        this.elementsStored = 0;
        this.cursor = 0;
        this.currentElement = null;
    }

    protected void setCursorToStart() {
        if ((this.cursor != 0 || this.currentElement != null) && this.storageShapes == null) {
            this.reset(); // This should only happen when storeResults was passed as false to the ctor by accident
        }
        this.cursor = 0;
    }

    @Override
    public boolean hasNext() {
        if (this.elementsStored > this.cursor) {
            return true;
        }
        return this.currentElement != null || this.produceNext();
    }

    @Override
    public VoxelShape next() {
        if (this.elementsStored > this.cursor) {
            VoxelShape storedShape = this.getOffsetStoredShape(this.cursor);
            this.cursor++;
            return storedShape;
        }
        this.cursor++;
        return this.getLastProduced();
    }

    /**
     * Use together with {@link  ChunkAwareBlockCollisionSweeperVoxelShape#currentOffsetX()},
     * {@link  ChunkAwareBlockCollisionSweeperVoxelShape#currentOffsetY()} and
     * {@link  ChunkAwareBlockCollisionSweeperVoxelShape#currentOffsetZ()}.
     * The position of the VoxelShape may or may not be missing the offset given by these methods.
     *
     * @return Maybe-offset VoxelShape
     */
    public VoxelShape nextWithoutOffset() {
        if (this.elementsStored > this.cursor) {
            VoxelShape storedShape = this.getStoredShape(this.cursor);
            this.cursor++;
            return storedShape;
        }
        this.cursor++;
        return this.getLastProducedWithoutOffset();
    }

    /**
     * Only use together with {@link ChunkAwareBlockCollisionSweeperVoxelShape#nextWithoutOffset()}
     * Undefined value if next() has been called afterward.
     *
     * @return offset corresponding to the last nextWithoutOffset() call.
     */
    public int currentOffsetX() {
        return this.pos.getX();
    }

    /**
     * Only use together with {@link ChunkAwareBlockCollisionSweeperVoxelShape#nextWithoutOffset()}
     * Undefined value if next() has been called afterward.
     *
     * @return offset corresponding to the last nextWithoutOffset() call.
     */
    public int currentOffsetY() {
        return this.pos.getY();
    }

    /**
     * Only use together with {@link ChunkAwareBlockCollisionSweeperVoxelShape#nextWithoutOffset()}
     * Undefined value if next() has been called afterward.
     *
     * @return offset corresponding to the last nextWithoutOffset() call.
     */
    public int currentOffsetZ() {
        return this.pos.getZ();
    }

    private void storeShapeWithOffset(VoxelShape next, int x, int y, int z) {
        if (this.storageShapes != null) {
            this.addToArrays(next, BlockPos.asLong(x, y, z));
        } else {
            this.currentElement = next;
            this.pos.set(x, y, z);
        }
    }

    private void addToArrays(VoxelShape next, long offset) {
        this.ensureStorageSize(this.elementsStored + 1);
        this.storageShapes[this.elementsStored] = next;
        this.storagePositions[this.elementsStored] = offset;
        this.elementsStored++;
    }

    /**
     * Gets a stored shape, with the block position offset already applied.
     *
     * @param index storage index
     * @return VoxelShape stored at the index with the correct block offset already applied
     */
    private VoxelShape getOffsetStoredShape(int index) {
        VoxelShape storedShape = this.storageShapes[index];

        //Store offset shapes to reduce allocations. Use nextNonOffset to avoid this allocation entirely.
        long storedShapePos = this.storagePositions[index];
        if (storedShapePos != 0) { //Packed 0 is BlockPos.ZERO, no offsetting of the shape needed
            this.storagePositions[index] = 0;
            storedShape = storedShape.move(BlockPos.getX(storedShapePos), BlockPos.getY(storedShapePos), BlockPos.getZ(storedShapePos));
            this.storageShapes[index] = storedShape;
        }
        return storedShape;
    }

    /**
     * Gets a stored shape and stores the offset in the mutable this.pos
     * Callers MUST check this.pos afterward, otherwise the returned shape's position is not well-defined.
     *
     * @param index storage index
     * @return VoxelShape stored at the index without offsetting by the block position
     */
    private VoxelShape getStoredShape(int index) {
        VoxelShape storedShape = this.storageShapes[index];

        //Store offset shapes to reduce allocations. Use nextNonOffset to avoid this allocation entirely.
        long storedShapePos = this.storagePositions[index];
        if (this.currentElement != null) {
            throw new IllegalStateException();
        }
        this.pos.set(storedShapePos);
        return storedShape;
    }

    private @NonNull VoxelShape getLastProduced() {
        VoxelShape next = this.currentElement;
        if (next == null) {
            throw new NoSuchElementException("Call hasNext() before next()!");
        }
        this.currentElement = null;
        return next.move(this.pos);
    }


    private @NonNull VoxelShape getLastProducedWithoutOffset() {
        VoxelShape next = this.currentElement;
        if (next == null) {
            throw new NoSuchElementException("Call hasNext() before next()!");
        }
        this.currentElement = null;
        return next;
    }


    /**
     * Advances the sweep forward until finding a block with a box-colliding {@link VoxelShape}.
     * <p>
     * Sets the mutable this.pos to the corresponding block position, used as offset for the returned shape later.
     * @return True if there is a next collided {@link VoxelShape} computed, or false when no collisions are left
     */
    public boolean produceNext() {
        while (true) {
            if (this.cIterated >= this.cTotalSize) {
                if (!this.nextSection()) {
                    break;
                }
            }

            this.cIterated++;


            final int x = this.cX;
            final int y = this.cY;
            final int z = this.cZ;

            //The iteration order within a chunk section is chosen so that it causes a mostly linear array access in the storage.
            //In net.minecraft.world.chunk.PalettedContainer.toIndex x gets the 4 least significant bits, z the 4 above, and y the 4 even higher ones.
            //Linearly accessing arrays is faster than other access patterns.
            if (this.cX < this.cEndX) {
                this.cX++;
            } else if (this.cZ < this.cEndZ) {
                this.cX = this.cStartX;
                this.cZ++;
            } else {
                this.cX = this.cStartX;
                this.cZ = this.cStartZ;
                this.cY++;
                //stop condition was already checked using this.cIterated at the start of the method
            }

            //using < minX and > maxX instead of <= and >= in vanilla, because minX, maxX are the coordinates
            //of the box that wasn't extended for oversized blocks yet.
            final int edgesHit = this.sectionOversizedBlocks ?
                    (x < this.minX || x > this.maxX ? 1 : 0) +
                            (y < this.minY || y > this.maxY ? 1 : 0) +
                            (z < this.minZ || z > this.maxZ ? 1 : 0) : 0;

            if (edgesHit == 3) {
                continue;
            }

            final BlockState state = this.cachedChunkSection.getBlockState(x & 15, y & 15, z & 15);

            if (!canInteractWithBlock(state, edgesHit)) {
                continue;
            }

            this.pos.set(x, y, z);
            if (this.currentElement != null) {
                throw new IllegalStateException();
            }

            VoxelShape collisionShape = this.context.getCollisionShape(state, this.world, this.pos);

            //noinspection ConstantValue
            if (collisionShape != null && collisionShape != Shapes.empty() /* collisionShape should never be null, but we received crash reports. */) {
                VoxelShape collidedShape = getNonOffsetCollidedShape(this.box, this.shape, collisionShape, x, y, z);
                if (collidedShape != null) {
                    if (z >= this.maxHitZ && (z > this.maxHitZ || y >= this.maxHitY && (y > this.maxHitY || x > this.maxHitX))) {
                        //Always make sure the shape at the maximum position is the last one returned, because
                        // the last shape has a different 1e-7 behavior (no next shape that clips movement to 0).
                        // This does affect certain contraptions: https://github.com/CaffeineMC/lithium-fabric/issues/443
                        VoxelShape previousMaxShape = this.maxShape;
                        this.maxShape = collidedShape;
                        int prevMaxX = this.maxHitX;
                        int prevMaxY = this.maxHitY;
                        int prevMaxZ = this.maxHitZ;
                        this.maxHitX = x;
                        this.maxHitY = y;
                        this.maxHitZ = z;
                        if (previousMaxShape != null) {
                            this.storeShapeWithOffset(previousMaxShape, prevMaxX, prevMaxY, prevMaxZ);
                            return true;
                        }
                    } else {
                        this.storeShapeWithOffset(collidedShape, x, y, z);
                        return true;
                    }
                }
            }
        }

        if (!this.hideMaxCollision && this.maxShape != null) {
            this.storeShapeWithOffset(this.maxShape, this.maxHitX, this.maxHitY, this.maxHitZ);
            this.maxShape = null;
            this.maxHitX = Integer.MIN_VALUE;
            this.maxHitY = Integer.MIN_VALUE;
            this.maxHitZ = Integer.MIN_VALUE;
            return true;
        }

        return false;
    }

    public List<VoxelShape> collectAll() {
        this.setCursorToStart();

        ArrayList<VoxelShape> collisions = new ArrayList<>();
        while (this.hasNext()) {
            collisions.add(this.next());
        }

        return collisions;
    }

    @Override
    public @NonNull ChunkAwareBlockCollisionSweeperVoxelShape iterator() {
        this.setCursorToStart();
        return this;
    }
}
