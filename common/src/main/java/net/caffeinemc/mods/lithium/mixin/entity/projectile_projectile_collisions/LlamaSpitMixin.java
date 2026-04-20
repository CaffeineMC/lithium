package net.caffeinemc.mods.lithium.mixin.entity.projectile_projectile_collisions;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.lithium.common.entity.projectile.ProjectileCanHitEntityPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(LlamaSpit.class)
public class LlamaSpitMixin {

    @Definition(id = "canHitEntity", method = "Lnet/minecraft/world/entity/projectile/Projectile;canHitEntity(Lnet/minecraft/world/entity/Entity;)Z")
    @Expression("this::canHitEntity")
    @ModifyExpressionValue(
            method = "tick", at = @At("MIXINEXTRAS:EXPRESSION")
    )
    private Predicate<Entity> getTypedPredicate(Predicate<Entity> original) {
        return new ProjectileCanHitEntityPredicate(original);
    }
}
