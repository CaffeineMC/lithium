package net.caffeinemc.mods.lithium.common.services;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.function.Predicate;

public interface PlatformEntityAccess {
    PlatformEntityAccess INSTANCE = Services.load(PlatformEntityAccess.class);

    void addEnderDragonParts(Level level, Entity excludedEntity, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities);

}
