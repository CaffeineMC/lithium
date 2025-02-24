package net.caffeinemc.mods.lithium.fabric;

import net.caffeinemc.mods.lithium.common.services.PlatformEntityAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.function.Predicate;

public class FabricEntityAccess implements PlatformEntityAccess {


    @Override
    public void addEnderDragonParts(Level level, Entity excludedEntity, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities) {
        for (EnderDragonPart enderDragonPart : level.dragonParts()) {
            if (enderDragonPart != excludedEntity
                    && enderDragonPart.parentMob != excludedEntity
                    && entityFilter.test(enderDragonPart) &&
                    box.intersects(enderDragonPart.getBoundingBox())
            ) {
                entities.add(enderDragonPart);
            }
        }
    }
}
