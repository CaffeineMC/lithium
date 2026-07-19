package net.caffeinemc.mods.lithium.common.entity.projectile;

import net.caffeinemc.mods.lithium.common.entity.EntityClassGroup;
import net.caffeinemc.mods.lithium.common.reflection.ReflectionUtil;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;

import java.util.function.Supplier;

public class ProjectileEntityClassGroups {

    /**
     * Projectiles that do not override {@link net.minecraft.world.entity.projectile.Projectile#canHitEntity(Entity)} or only do so to restrict the set of hittable entities
     */
    @SuppressWarnings("JavadocReference")
    public static final EntityClassGroup OPTIMIZED_PROJECTILES;
    /**
     * Projectiles that override {@link Entity#canBeHitByProjectile()} or override {@link Entity#isPickable()} in a way that makes them hittable by projectiles.
     */
    public static final EntityClassGroup CAN_MAYBE_BE_HIT_BY_OPTIMIZED_PROJECTILE;


    static {
        String remapped_canHitEntity = "canHitEntity";
        OPTIMIZED_PROJECTILES = new EntityClassGroup(
                (Class<?> entityClass, Supplier<EntityType<?>> entityType) -> {
                    if (!Projectile.class.isAssignableFrom(entityClass)) {
                        return false;
                    }
                    Class<?> parentClass = Projectile.class;
                    if (AbstractHurtingProjectile.class.isAssignableFrom(entityClass)) {
                        parentClass = AbstractHurtingProjectile.class;
                        if (AbstractWindCharge.class.isAssignableFrom(entityClass)) {
                            parentClass = AbstractWindCharge.class;
                        }
                    } else if (AbstractArrow.class.isAssignableFrom(entityClass)) {
                        parentClass = AbstractArrow.class;
                    } else if (ShulkerBullet.class.isAssignableFrom(entityClass)) {
                        parentClass = ShulkerBullet.class;
                    }

                    return !ReflectionUtil.hasMethodOverride(entityClass, parentClass, true, remapped_canHitEntity, Entity.class);
                });

        String remapped_canBeHitByProjectile = "canBeHitByProjectile";
        String remapped_isPickable = "isPickable";
        CAN_MAYBE_BE_HIT_BY_OPTIMIZED_PROJECTILE = new EntityClassGroup(
                (entityClass, entityType) -> {
                    Class<?> parentClass_isPickable = Entity.class;
                    if (Interaction.class == entityClass) {
                        return false;
                    }
                    if (ReflectionUtil.hasMethodOverride(entityClass, Entity.class, true, remapped_canBeHitByProjectile)) {
                        return true;
                    }
                    if (EnderDragon.class == entityClass) {
                        return false;
                    }
                    if (Projectile.class.isAssignableFrom(entityClass)) {
                        parentClass_isPickable = Projectile.class;
                        if (AbstractArrow.class.isAssignableFrom(entityClass)) {
                            parentClass_isPickable = AbstractArrow.class;
                        }
                        //noinspection deprecation
                        if (entityType.get().builtInRegistryHolder().is(EntityTypeTags.REDIRECTABLE_PROJECTILE)) {
                            return true;
                        }
                    }
                    return ReflectionUtil.hasMethodOverride(entityClass, parentClass_isPickable, true, remapped_isPickable);
                });
    }
}
