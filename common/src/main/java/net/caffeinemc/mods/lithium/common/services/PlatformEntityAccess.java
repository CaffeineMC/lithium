package net.caffeinemc.mods.lithium.common.services;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.function.Predicate;

public interface PlatformEntityAccess {
    PlatformEntityAccess INSTANCE = Services.load(PlatformEntityAccess.class);

    //Due to the differences in 1.21.1 NeoForge and Fabric, only one of the two following methods should be adding entities to the list, while the other should be a NO-OP
    void addPartEntities(Level level, Entity excludedEntity, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities);

    void addSubEntities(Level level, EnderDragon enderDragon, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities);

}
