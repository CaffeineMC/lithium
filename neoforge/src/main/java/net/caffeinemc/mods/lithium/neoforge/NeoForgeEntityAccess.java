package net.caffeinemc.mods.lithium.neoforge;

import net.caffeinemc.mods.lithium.common.services.PlatformEntityAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.function.Predicate;

public class NeoForgeEntityAccess implements PlatformEntityAccess {

    @Override
    public void addEnderDragonParts(Level level, Entity excludedEntity, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities) {
        //  [NeoForge Copy] Support for dragon parts and neoforge part entities
        for (PartEntity<?> partEntity : level.dragonParts()) {
            if (partEntity != excludedEntity
                    && partEntity.getParent() != excludedEntity
                    && entityFilter.test(partEntity)
                    && box.intersects(partEntity.getBoundingBox())
            ) {
                entities.add(partEntity);
            }
        }
    }
}
