package net.caffeinemc.mods.lithium.common.entity.projectile;

import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

public record ProjectileCanHitEntityPredicate(Predicate<Entity> originalPredicate) implements Predicate<Entity> {
    @Override
    public boolean test(Entity entity) {
        return this.originalPredicate.test(entity);
    }
}
