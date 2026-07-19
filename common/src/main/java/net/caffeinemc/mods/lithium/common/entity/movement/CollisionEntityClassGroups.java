package net.caffeinemc.mods.lithium.common.entity.movement;

import net.caffeinemc.mods.lithium.common.entity.EntityClassGroup;
import net.caffeinemc.mods.lithium.common.reflection.ReflectionUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.vehicle.minecart.Minecart;

import java.util.function.Supplier;
import java.util.logging.Logger;

public class CollisionEntityClassGroups {
    public static final EntityClassGroup CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE; //aka entities that will attempt to collide with all other entities when moving
    public static final EntityClassGroup BOAT_SHULKER_LIKE_COLLISION; //aka entities that other entities will do block-like collisions with when moving


    static {
        CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE = new EntityClassGroup(
                (Class<?> entityClass, Supplier<EntityType<?>> _) -> ReflectionUtil.hasMethodOverride(entityClass, Entity.class, true, "canCollideWith", Entity.class));

        //sanity check: in case method names changed, fail
        if ((!CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(Minecart.class, EntityTypes.MINECART))) {
            throw new AssertionError();
        }
        if ((!CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(WindCharge.class, EntityTypes.WIND_CHARGE)) || (!CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(BreezeWindCharge.class, EntityTypes.BREEZE_WIND_CHARGE))) {
            throw new AssertionError();
        }
        if ((CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(Shulker.class, EntityTypes.SHULKER))) {
            //should not throw an Error here, because another mod *could* add the method to ShulkerEntity. Warning when this sanity check fails.
            Logger.getLogger("Lithium EntityClassGroup").warning("Either Lithium EntityClassGroup is broken or something else gave Shulkers the minecart-like collision behavior.");
        }
        CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.clear();

        BOAT_SHULKER_LIKE_COLLISION = new EntityClassGroup(
                (Class<?> entityClass, Supplier<EntityType<?>> _) -> ReflectionUtil.hasMethodOverride(entityClass, Entity.class, true, "canBeCollidedWith", Entity.class));

        //sanity check: in case method names changed, fail
        if ((!BOAT_SHULKER_LIKE_COLLISION.contains(Shulker.class, EntityTypes.SHULKER))) {
            throw new AssertionError();
        }
        BOAT_SHULKER_LIKE_COLLISION.clear();
    }
}
