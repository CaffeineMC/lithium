package net.caffeinemc.mods.lithium.common.world;

import net.caffeinemc.mods.lithium.common.explosion.DirectMappedPos2AABBsCache;
import net.caffeinemc.mods.lithium.common.explosion.ExplosionEntityRays;
import net.caffeinemc.mods.lithium.common.util.ArrayConstants;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.world.explosions.ClipContextAccess;
import net.caffeinemc.mods.lithium.mixin.world.raycast.ClipContextAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;

public sealed abstract class BlockHitFactory implements BiFunction<ClipContext, BlockPos, BlockHitResult> {
    private static final BlockHitResult DUMMY_HIT = new BlockHitResult(Vec3.ZERO, Direction.NORTH, BlockPos.ZERO, false);

    protected final Level level;
    private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
    private ChunkAccess chunk = null;

    protected BlockHitFactory(Level level) {
        this.level = level;
    }

    public BlockState getBlock(BlockPos blockPos) {
        if (this.level.isOutsideBuildHeight(blockPos.getY())) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        int chunkX = Pos.ChunkCoord.fromBlockCoord(blockPos.getX());
        int chunkZ = Pos.ChunkCoord.fromBlockCoord(blockPos.getZ());

        // Avoid calling into the chunk manager as much as possible through managing chunks locally
        if (this.chunkX != chunkX || this.chunkZ != chunkZ) {
            this.chunk = this.level.getChunk(chunkX, chunkZ);

            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        final ChunkAccess chunk = this.chunk;

        // If the chunk is missing or out of bounds, assume that it is air
        if (chunk != null) {
            // We operate directly on chunk sections to avoid interacting with BlockPos and to squeeze out as much
            // performance as possible here
            LevelChunkSection section = chunk.getSections()[Pos.SectionYIndex.fromBlockCoord(chunk, blockPos.getY())];

            // If the section doesn't exist or is empty, assume that the block is air
            if (section != null && !section.hasOnlyAir()) {
                return section.getBlockState(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15);
            }
        }

        return Blocks.AIR.defaultBlockState();
    }

    public static final class EntityExplosion extends BlockHitFactory {
        private final DirectMappedPos2AABBsCache cache;

        public EntityExplosion(Level level) {
            super(level);
            this.cache = DirectMappedPos2AABBsCache.BLOCK_CACHE_TL.get();
            this.cache.invalidate();
        }

        // Specialized raycast without fluid handling & hit direction computation and with inlined block shape getter
        @Override
        public BlockHitResult apply(ClipContext clipContext, BlockPos blockPos) {
            long posLong = blockPos.asLong();
            AABB[] aabbs = this.cache.getEntry(posLong);
            VoxelShape collisionShape1;
            if (aabbs == null) {
                BlockState state = getBlock(blockPos);
                collisionShape1 = state.getCollisionShape(this.level, blockPos, ((ClipContextAccess) clipContext).lithium$getCollisionContext());
                if (collisionShape1.isEmpty()) {
                    aabbs = ArrayConstants.EMPTY_AABBS;
                } else {
                    aabbs = collisionShape1.toAabbs().toArray(AABB[]::new);
                }

                this.cache.cacheEntry(aabbs, posLong);
            }

            boolean wasHit = aabbs.length > 0 && ExplosionEntityRays.doesRayHitOffsetAABBVolumes(aabbs, blockPos, clipContext.getFrom(), clipContext.getTo());
            return wasHit ? DUMMY_HIT : null;
        }
    }

    public static final class Generic extends BlockHitFactory {
        private final boolean handleFluids;

        public Generic(Level level, ClipContext context) {
            super(level);
            this.handleFluids = ((ClipContextAccessor) context).getFluidHandling() != ClipContext.Fluid.NONE;
        }

        @Override
        public BlockHitResult apply(ClipContext clipContext, BlockPos blockPos) {
            //[VanillaCopy] BlockView.raycast, but optional fluid handling
            BlockState blockState = this.getBlock(blockPos);
            Vec3 start = clipContext.getFrom();
            Vec3 end = clipContext.getTo();
            VoxelShape blockShape = clipContext.getBlockShape(blockState, this.level, blockPos);
            BlockHitResult blockHitResult = this.level.clipWithInteractionOverride(start, end, blockPos, blockShape, blockState);
            double d = blockHitResult == null ? Double.MAX_VALUE : clipContext.getFrom().distanceToSqr(blockHitResult.getLocation());
            double e = Double.MAX_VALUE;
            BlockHitResult fluidHitResult = null;
            if (this.handleFluids) {
                FluidState fluidState = blockState.getFluidState();
                VoxelShape fluidShape = clipContext.getFluidShape(fluidState, this.level, blockPos);
                fluidHitResult = fluidShape.clip(start, end, blockPos);
                e = fluidHitResult == null ? Double.MAX_VALUE : clipContext.getFrom().distanceToSqr(fluidHitResult.getLocation());
            }
            return d <= e ? blockHitResult : fluidHitResult;
        }
    }
}
