package net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.skip_air;

import net.caffeinemc.mods.lithium.common.explosion.LithiumExplosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin implements LithiumExplosion {
    @Shadow
    @Final
    private float radius;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private boolean fire;
    @Shadow
    @Final
    private Vec3 center;


    @Inject(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;Lnet/minecraft/world/phys/Vec3;FZLnet/minecraft/world/level/Explosion$BlockInteraction;)V",
            at = @At("RETURN")
    )
    private void init(ServerLevel serverLevel, Entity entity, DamageSource damageSource, ExplosionDamageCalculator explosionDamageCalculator, Vec3 vec3, float f, boolean bl, Explosion.BlockInteraction blockInteraction, CallbackInfo ci) {
        // air blocks are only relevant for the explosion when fire should be created inside them or
        // when block updates created by the explosion replace the blocks (non-vanilla behavior of this optimization)

        //To work around the case of exploding end portals placed by an explosion destroying the end crystal that is
        // currently respawning the ender dragon, this optimization is only applies when far enough away
        if (!this.fire && this.level.dimension() == Level.END && this.level.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) {
            float overestimatedExplosionRange = (8 + (int) (6f * this.radius));
            int endPortalX = 0;
            int endPortalZ = 0;
            if (overestimatedExplosionRange > Math.abs(this.center.x - endPortalX) && overestimatedExplosionRange > Math.abs(this.center.z - endPortalZ)) {
                //Could check if the dragon fight is in respawn phase here as well, but that requires even more mixins
                return;
            }
        }
        this.lithium$setSkipAir();
    }

}
