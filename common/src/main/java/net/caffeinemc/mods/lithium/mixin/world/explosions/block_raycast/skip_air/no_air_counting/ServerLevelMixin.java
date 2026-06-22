package net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.skip_air.no_air_counting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.caffeinemc.mods.lithium.common.explosion.LithiumExplosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, priority = 10000)
public class ServerLevelMixin {

    @WrapOperation(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;explode()I")
    )
    private int delayExploding(ServerExplosion instance, Operation<Integer> original, @Share("explodeOperation") LocalRef<Operation<Integer>> explodeOperation) {
        if (instance instanceof LithiumExplosion lithiumExplosion && lithiumExplosion.lithium$isSkippingAir()) {
            explodeOperation.set(original);
            return -1;
        }
        return original.call(instance);
    }

    @ModifyVariable(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;getHitPlayers()Ljava/util/Map;"),
            name = "blockCount"
    )
    private int runDelayedExplosion(int blockCount, @Local(name = "explosion") ServerExplosion explosion, @Share("explodeOperation") LocalRef<Operation<Integer>> explodeOperation) {
        Operation<Integer> explosionCall = explodeOperation.get();
        if (explosionCall != null) {
            blockCount = explosionCall.call(explosion);
            explodeOperation.set(null);
        }
        return blockCount;
    }

    @Inject(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At("RETURN")
    )
    private void runDelayedExplosionWithoutCountingBlocks(CallbackInfo ci, @Local(name = "explosion") ServerExplosion explosion, @Share("explodeOperation") LocalRef<Operation<Integer>> explodeOperation) {
        Operation<Integer> explosionCall = explodeOperation.get();
        if (explosionCall != null) {
            if (explosion instanceof LithiumExplosion lithiumExplosion) {
                lithiumExplosion.lithium$setSkipAirWithoutCounting();
            }
            explosionCall.call(explosion);
            explodeOperation.set(null);
        }
    }
}
