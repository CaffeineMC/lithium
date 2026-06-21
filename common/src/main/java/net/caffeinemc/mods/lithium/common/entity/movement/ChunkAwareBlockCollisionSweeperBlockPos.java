package net.caffeinemc.mods.lithium.common.entity.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;


public class ChunkAwareBlockCollisionSweeperBlockPos extends ChunkAwareBlockCollisionSweeper<BlockPos.MutableBlockPos> {

    public ChunkAwareBlockCollisionSweeperBlockPos(Level world, @Nullable Entity entity, AABB box) {
        super(world, entity, box);
    }


    //Fields for iterator
    private BlockPos.MutableBlockPos computedNext;

    @Override
    public boolean hasNext() {
        return this.computedNext != null || ((this.computedNext = this.computeNext()) != null);
    }

    @Override
    public BlockPos.MutableBlockPos next() {
        BlockPos.MutableBlockPos next = this.computedNext;
        if (next == null) {
            throw new NoSuchElementException("Call hasNext() before next()!");
        }
        this.computedNext = null;
        return next;
    }

    /**
     * Advances the sweep forward until finding a position with a box-colliding block.
     *
     * @return the next position with a collision as {@link BlockPos.MutableBlockPos}, or null when no collisions
     * are left
     */
    public BlockPos.MutableBlockPos computeNext() {
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

            VoxelShape collisionShape = this.context.getCollisionShape(state, this.world, this.pos);

            //noinspection ConstantValue
            if (collisionShape != null && collisionShape != Shapes.empty() /* collisionShape should never be null, but we received crash reports. */) {
                if (isColliding(this.box, this.shape, collisionShape, x, y, z)) {
                    return this.pos;
                }
            }
        }

        return null;
    }

}
