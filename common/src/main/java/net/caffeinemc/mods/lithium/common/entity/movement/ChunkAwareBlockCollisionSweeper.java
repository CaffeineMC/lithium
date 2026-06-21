package net.caffeinemc.mods.lithium.common.entity.movement;

import net.caffeinemc.mods.lithium.common.block.BlockCountingSection;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeCaster;
import net.caffeinemc.mods.lithium.common.shapes.offset_operations.LithiumOffsetShapes;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

import static net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions.EPSILON;

/**
 * ChunkAwareBlockCollisionSweeper iterates over blocks in one chunk section at a time. Together with the chunk
 * section keeping track of the amount of oversized blocks inside the number of iterations can often be reduced.
 */
public abstract class ChunkAwareBlockCollisionSweeper<T> implements Iterator<T> {
    protected final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(); //Must only be modified by computeNext, as other methods rely on the position not being changed

    /**
     * The collision box being swept through the world.
     */
    protected final AABB box;

    /**
     * The VoxelShape of the collision box being swept through the world.
     */
    protected final VoxelShape shape;

    protected final Level world;

    protected final CollisionContext context;

    //limits of the area without extension for oversized blocks
    protected final int minX, minY, minZ, maxX, maxY, maxZ;

    //variables prefixed with c refer to the iteration of the currently cached chunk section
    private int chunkX, chunkYIndex, chunkZ;
    protected int cStartX, cStartZ;
    protected int cEndX, cEndZ;
    protected int cX, cY, cZ;

    protected int cTotalSize;
    protected int cIterated;

    protected boolean sectionOversizedBlocks;
    private ChunkAccess cachedChunk;
    protected LevelChunkSection cachedChunkSection;

    public ChunkAwareBlockCollisionSweeper(Level world, @Nullable Entity entity, AABB box) {
        this.box = box;
        this.shape = Shapes.create(box);
        this.context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
        this.world = world;

        this.minX = Mth.floor(box.minX - EPSILON);
        this.maxX = Mth.floor(box.maxX + EPSILON);
        this.minY = Mth.clamp(Mth.floor(box.minY - EPSILON), Pos.BlockCoord.getMinY(this.world), Pos.BlockCoord.getMaxYInclusive(this.world));
        this.maxY = Mth.clamp(Mth.floor(box.maxY + EPSILON), Pos.BlockCoord.getMinY(this.world), Pos.BlockCoord.getMaxYInclusive(this.world));
        this.minZ = Mth.floor(box.minZ - EPSILON);
        this.maxZ = Mth.floor(box.maxZ + EPSILON);

        this.chunkX = Pos.ChunkCoord.fromBlockCoord(expandMin(this.minX));
        this.chunkZ = Pos.ChunkCoord.fromBlockCoord(expandMin(this.minZ));

        this.cIterated = 0;
        this.cTotalSize = 0;

        //decrement as first nextSection call will increment it again
        this.chunkX--;
    }


    protected void reset() {
        this.chunkYIndex = 0;
        this.cStartX = 0;
        this.cStartZ = 0;
        this.cEndX = 0;
        this.cEndZ = 0;
        this.cX = 0;
        this.cY = 0;
        this.cZ = 0;
        this.sectionOversizedBlocks = false;
        this.cachedChunk = null;
        this.cachedChunkSection = null;

        this.chunkX = Pos.ChunkCoord.fromBlockCoord(expandMin(this.minX));
        this.chunkZ = Pos.ChunkCoord.fromBlockCoord(expandMin(this.minZ));

        this.cIterated = 0;
        this.cTotalSize = 0;

        //decrement as first nextSection call will increment it again
        this.chunkX--;
        this.pos.set(0, 0, 0);
    }

    final protected boolean nextSection() {
        do {
            do {
                // Find the coordinates of the next section inside the area expanded by 1 block on all sides.
                // Note: this.minX/maxX/etc are not expanded, so there are lots of +1 and -1 around.
                if (
                        this.cachedChunk != null &&
                                this.chunkYIndex < Pos.SectionYIndex.getMaxYSectionIndexInclusive(this.world) &&
                                this.chunkYIndex < Pos.SectionYIndex.fromBlockCoord(this.world, expandMax(this.maxY))
                ) {
                    this.chunkYIndex++;
                    this.cachedChunkSection = this.cachedChunk.getSections()[this.chunkYIndex];
                } else {
                    if (this.chunkX < Pos.ChunkCoord.fromBlockCoord(expandMax(this.maxX))) {
                        //first initialization takes this branch
                        this.chunkX++;
                    } else {
                        if (this.chunkZ < Pos.ChunkCoord.fromBlockCoord(expandMax(this.maxZ))) {
                            this.chunkX = Pos.ChunkCoord.fromBlockCoord(expandMin(this.minX));
                            this.chunkZ++;
                        } else {
                            // Important: No field assignment / mutation happens in the code path to this, so
                            // consecutive nextSection calls keep returning false, instead of working on invalid data.
                            // Otherwise, additional chunk sections would be iterated incorrectly:
                            // https://github.com/CaffeineMC/lithium/issues/628
                            return false; // no more sections to iterate
                        }
                    }
                    this.cachedChunk = this.world.getChunk(this.chunkX, this.chunkZ, ChunkStatus.FULL, false);
                    if (this.cachedChunk != null) {
                        this.chunkYIndex = Mth.clamp(
                                Pos.SectionYIndex.fromBlockCoord(this.world, expandMin(this.minY)),
                                Pos.SectionYIndex.getMinYSectionIndex(this.world),
                                Pos.SectionYIndex.getMaxYSectionIndexInclusive(this.world)
                        );
                        this.cachedChunkSection = this.cachedChunk.getSections()[this.chunkYIndex];
                    }
                }
                //skip empty chunks and empty chunk sections
            } while (this.cachedChunk == null || this.cachedChunkSection == null || this.cachedChunkSection.hasOnlyAir());

            this.sectionOversizedBlocks = hasChunkSectionOversizedBlocks(this.cachedChunk, this.chunkYIndex);

            int sizeExtension = this.sectionOversizedBlocks ? 1 : 0;

            this.cEndX = Math.min(this.maxX + sizeExtension, Pos.BlockCoord.getMaxInSectionCoord(this.chunkX));
            int cEndY = Math.min(this.maxY + sizeExtension, Pos.BlockCoord.getMaxYInSectionIndex(this.world, this.chunkYIndex));
            this.cEndZ = Math.min(this.maxZ + sizeExtension, Pos.BlockCoord.getMaxInSectionCoord(this.chunkZ));

            this.cStartX = Math.max(this.minX - sizeExtension, Pos.BlockCoord.getMinInSectionCoord(this.chunkX));
            int cStartY = Math.max(this.minY - sizeExtension, Pos.BlockCoord.getMinYInSectionIndex(this.world, this.chunkYIndex));
            this.cStartZ = Math.max(this.minZ - sizeExtension, Pos.BlockCoord.getMinInSectionCoord(this.chunkZ));
            this.cX = this.cStartX;
            this.cY = cStartY;
            this.cZ = this.cStartZ;

            this.cTotalSize = (this.cEndX - this.cStartX + 1) * (cEndY - cStartY + 1) * (this.cEndZ - this.cStartZ + 1);
            //skip completely empty section iterations
        } while (this.cTotalSize == 0);
        this.cIterated = 0;

        return true;
    }

    /**
     * This is an artifact from vanilla which is used to avoid testing shapes in the extended portion of a volume
     * unless they are a shape which exceeds their voxel. Pistons must be special-cased here.
     *
     * @return True if the shape can be interacted with at the given edge boundary
     */
    protected static boolean canInteractWithBlock(BlockState state, int edgesHit) {
        return (edgesHit != 1 || state.hasLargeCollisionShape()) && (edgesHit != 2 || state.getBlock() == Blocks.MOVING_PISTON);
    }

    /**
     * Checks if the {@code entityShape} or {@code entityBox} intersects the given {@code shape} which is translated
     * to the given position. This is a very specialized implementation which tries to avoid going through
     * {@link VoxelShape} for full-cube shapes.
     *
     * @return a {@link VoxelShape} which contains the shape representing that which was collided with, otherwise
     * {@code null}. Note that the returned shape is not yet shifted by the given x,y,z coordinates.
     */
    protected static VoxelShape getNonOffsetCollidedShape(AABB entityBox, VoxelShape entityShape, VoxelShape shape, int x, int y, int z) {
        if (shape == Shapes.block()) {
            //[VanillaCopy] {@link BlockCollisions#computeNext()}
            return entityBox.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0) ? shape : null;
        }
        if (shape instanceof VoxelShapeCaster) {
            if (((VoxelShapeCaster) shape).intersects(entityBox, x, y, z)) {
                return shape;
            } else {
                return null;
            }
        }

        if (LithiumOffsetShapes.joinIsNotEmpty(shape, x, y, z, entityBox, entityShape, BooleanOp.AND)) {
            return shape;
        }

        return null;
    }

    //Equivalent of getCollidedShape(AABB entityBox, VoxelShape entityShape, VoxelShape shape, int x, int y, int z) != null
    protected static boolean isColliding(AABB entityBox, VoxelShape entityShape, VoxelShape shape, int x, int y, int z) {
        if (shape == Shapes.block()) {
            return entityBox.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        }
        if (shape instanceof VoxelShapeCaster) {
            return ((VoxelShapeCaster) shape).intersects(entityBox, x, y, z);
        }
        return LithiumOffsetShapes.joinIsNotEmpty(shape, x, y, z, entityBox, entityShape, BooleanOp.AND);
    }

    private static int expandMin(int coord) {
        return coord - 1;
    }
    private static int expandMax(int coord) {
        return coord + 1;
    }

    /**
     * Checks the cached information whether the {@code chunkY} section of the {@code chunk} has oversized blocks.
     *
     * @return whether there are any oversized blocks in the chunk section
     */
    private static boolean hasChunkSectionOversizedBlocks(ChunkAccess chunk, int chunkY) {
        if (BlockStateFlags.ENABLED) {
            LevelChunkSection section = chunk.getSections()[chunkY];
            return section != null && ((BlockCountingSection) section).lithium$mayContainAny(BlockStateFlags.OVERSIZED_SHAPE);
        }
        return true; //like vanilla, assume that a chunk section has oversized blocks, when the section mixin isn't loaded
    }
}
