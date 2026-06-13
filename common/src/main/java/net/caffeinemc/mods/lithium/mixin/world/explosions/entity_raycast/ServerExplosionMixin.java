package net.caffeinemc.mods.lithium.mixin.world.explosions.entity_raycast;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.caffeinemc.mods.lithium.common.explosion.DirectMappedPos2AABBsCache;
import net.caffeinemc.mods.lithium.common.explosion.ExplosionEntityRays;
import net.caffeinemc.mods.lithium.common.util.ArrayConstants;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.world.explosions.ClipContextAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/**
 * @author Crosby
 */
@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {
    @SuppressWarnings("DataFlowIssue")
    @Unique
    private static final BlockHitResult MISS = BlockHitResult.miss(null, null, null);

    /**
     * Pre-allocate our {@link ClipContextAccess}.
     * @author Crosby
     */
    @Inject(
            method = "getSeenPercent",
            at = @At("HEAD")
    )
    private static void createMutableContext(Vec3 to, Entity entity, CallbackInfoReturnable<Float> cir, @Share("blockHitFactory") LocalRef<BiFunction<ClipContext, BlockPos, BlockHitResult>> hitFactoryRef) {
        hitFactoryRef.set(blockHitFactory(entity));
    }

    /**
     * Remove {@link ClipContext} allocation.
     * @author Crosby
     */
    @Redirect(
            method = "getSeenPercent",
            at = @At(value = "NEW", target = "net/minecraft/world/level/ClipContext")
    )
    private static ClipContext reuseClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity, @Share("context") LocalRef<ClipContext> contextRef) {
        ClipContext clipContext = contextRef.get();
        if (clipContext == null) {
            clipContext = new ClipContext(from, to, block, fluid, entity);
            contextRef.set(clipContext);
        } else {
            ((ClipContextAccess) clipContext).lithium$setFrom(from);
        }
        return clipContext;
    }

    /**
     * Use specialized hit factory and remove miss allocation.
     * @author Crosby
     */
    @Redirect(
            method = "getSeenPercent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;")
    )
    private static BlockHitResult simplifyRaycast(Level level, ClipContext clipContext, @Share("blockHitFactory") LocalRef<BiFunction<ClipContext, BlockPos, BlockHitResult>> hitFactoryRef) {
        return BlockGetter.traverseBlocks(clipContext.getFrom(), clipContext.getTo(), clipContext, hitFactoryRef.get(), ctx -> MISS);
    }

    @Unique
    private static final BlockHitResult DUMMY_HIT = new BlockHitResult(Vec3.ZERO, Direction.NORTH, BlockPos.ZERO, false);
    /**
     * Specialized version of {@link net.caffeinemc.mods.lithium.mixin.world.raycast.BlockGetterMixin#blockHitFactory(ClipContext)}
     * reusing the {@link ClipContext} by repeatedly adjust the raycast from position,
     * eliminating extra allocations from the loop body of {@link ServerExplosion#getSeenPercent(Vec3, Entity)}.
     * We also remove fluid handling and hit direction computation.
     * @author Crosby
     */
    @Unique
    private static BiFunction<ClipContext, BlockPos, BlockHitResult> blockHitFactory(Entity entity) {
        return new BiFunction<>() {
            final Level level = entity.level();
            final DirectMappedPos2AABBsCache cache;

            {
                this.cache = DirectMappedPos2AABBsCache.BLOCK_CACHE_TL.get();
                this.cache.invalidate();
            }

            int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
            ChunkAccess chunk = null;

            @Override
            public BlockHitResult apply(ClipContext clipContext, BlockPos blockPos) {
                long posLong = blockPos.asLong();
                AABB[] aabbs = this.cache.getEntry(posLong);
                VoxelShape collisionShape1;
                if (aabbs == null) {
                    BlockState state = getBlock(this.level, blockPos);
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

            //Code duplicated from BlockGetterMixin
            private BlockState getBlock(LevelReader world, BlockPos blockPos) {
                if (world.isOutsideBuildHeight(blockPos.getY())) {
                    return Blocks.VOID_AIR.defaultBlockState();
                }
                int chunkX = Pos.ChunkCoord.fromBlockCoord(blockPos.getX());
                int chunkZ = Pos.ChunkCoord.fromBlockCoord(blockPos.getZ());

                // Avoid calling into the chunk manager as much as possible through managing chunks locally
                if (this.chunkX != chunkX || this.chunkZ != chunkZ) {
                    this.chunk = world.getChunk(chunkX, chunkZ);

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
        };
    }

}
