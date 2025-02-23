package net.caffeinemc.mods.lithium.mixin.world.explosions.entity_raycast;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.world.explosions.MutableExplosionClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
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
    @SuppressWarnings("DataFlowIssue")
    @Unique
    private static final ClipContext EMPTY = new ClipContext(null, null, null, null, (CollisionContext) null);

    /**
     * Pre-allocate our {@link MutableExplosionClipContext}.
     * @author Crosby
     */
    @Inject(
            method = "getSeenPercent",
            at = @At("HEAD")
    )
    private static void createMutableContext(Vec3 to, Entity entity, CallbackInfoReturnable<Float> cir, @Share("context") LocalRef<MutableExplosionClipContext> contextRef, @Share("blockHitFactory") LocalRef<BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult>> hitFactoryRef) {
        contextRef.set(new MutableExplosionClipContext(entity.level(), to));
        hitFactoryRef.set(blockHitFactory());
    }

    /**
     * Remove {@link ClipContext} allocation.
     * @author Crosby
     */
    @Redirect(
            method = "getSeenPercent",
            at = @At(value = "NEW", target = "net/minecraft/world/level/ClipContext")
    )
    private static ClipContext removeUnusedObject(Vec3 from, Vec3 to, ClipContext.Block p_45690_, ClipContext.Fluid p_45691_, Entity entity, @Share("context") LocalRef<MutableExplosionClipContext> contextRef) {
        contextRef.get().from = from;
        return EMPTY;
    }

    /**
     * Use specialized hit factory and remove miss allocation.
     * @author Crosby
     */
    @Redirect(
            method = "getSeenPercent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;")
    )
    private static BlockHitResult simplifyRaycast(Level level, ClipContext nullContext, @Share("context") LocalRef<MutableExplosionClipContext> contextRef, @Share("blockHitFactory") LocalRef<BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult>> hitFactoryRef) {
        MutableExplosionClipContext context = contextRef.get();
        BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult> blockHitFactory = hitFactoryRef.get();
        return BlockGetter.traverseBlocks(context.from, context.to, context, blockHitFactory, ctx -> MISS);
    }

    /**
     * Specialized version of {@link net.caffeinemc.mods.lithium.mixin.world.raycast.BlockGetterMixin#blockHitFactory(ClipContext)}
     * where the inlined shape getter allows us to replace the {@link ClipContext} with our own {@link MutableExplosionClipContext},
     * eliminating extra allocations from the loop body of {@link ServerExplosion#getSeenPercent(Vec3, Entity)}.
     * We also remove fluid handling and hit direction computation.
     * @author Crosby
     */
    @Unique
    private static BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult> blockHitFactory() {
        return new BiFunction<>() {
            int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
            ChunkAccess chunk = null;

            @Override
            public BlockHitResult apply(MutableExplosionClipContext context, BlockPos blockPos) {
                BlockState state = getBlock(context.level, blockPos);

                return state.getCollisionShape(context.level, blockPos).clip(context.from, context.to, blockPos);
            }

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
