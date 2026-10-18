package net.caffeinemc.mods.lithium.mixin.world.explosions.entity_raycast;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.caffeinemc.mods.lithium.common.world.BlockHitFactory;
import net.caffeinemc.mods.lithium.common.world.explosions.ClipContextAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        hitFactoryRef.set(new BlockHitFactory.EntityExplosion(entity.level()));
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
}
