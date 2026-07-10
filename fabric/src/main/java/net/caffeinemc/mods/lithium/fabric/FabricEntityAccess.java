package net.caffeinemc.mods.lithium.fabric;

import net.caffeinemc.mods.lithium.common.services.PlatformEntityAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.function.Predicate;

public class FabricEntityAccess implements PlatformEntityAccess {


    @Override
    public void addPartEntities(Level level, Entity excludedEntity, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities) {
        
    }

    @Override
    public void addSubEntities(Level level, EnderDragon enderDragon, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities) {
        for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
            if (entityFilter.test(enderDragonPart)) {
                entities.add(enderDragonPart); //Note: Vanilla does not check intersection of bounding boxes for EnderDragonPart (the EnderDragon needs to intersect though)
            }
        }
    }
}
