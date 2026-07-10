package net.caffeinemc.mods.lithium.neoforge;

import net.caffeinemc.mods.lithium.common.services.PlatformEntityAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.function.Predicate;

public class NeoForgeEntityAccess implements PlatformEntityAccess {

    @Override
    public void addPartEntities(Level level, Entity excludedEntity, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities) {
        //  [NeoForge Copy] Support for dragon parts and neoforge part entities
        for (PartEntity<?> partEntity : level.getPartEntities()) {
            if (partEntity != excludedEntity
                    && partEntity.getParent() != excludedEntity
                    && entityFilter.test(partEntity)
                    //Note: Neoforge does (!!!) check intersection of bounding boxes for EnderDragonPart. Different from vanilla.
                    && box.intersects(partEntity.getBoundingBox())
            ) {
                entities.add(partEntity);
            }
        }
    }

    @Override
    public void addSubEntities(Level level, EnderDragon enderDragon, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities) {

    }
}
