package net.caffeinemc.mods.lithium.mixin.entity.projectile_projectile_collisions;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.lithium.common.entity.projectile.ProjectileCanHitEntityPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(AbstractHurtingProjectile.class)
public class AbstractHurtingProjectileMixin {

    @Definition(id = "canHitEntity", method = "Lnet/minecraft/world/entity/projectile/hurtingprojectile/AbstractHurtingProjectile;canHitEntity(Lnet/minecraft/world/entity/Entity;)Z")
    @Expression("this::canHitEntity")
    @ModifyExpressionValue(
            method = "tick", at = @At("MIXINEXTRAS:EXPRESSION")
    )
    private Predicate<Entity> getTypedPredicate(Predicate<Entity> original) {
        return new ProjectileCanHitEntityPredicate(original);
    }
}
