package net.caffeinemc.mods.lithium.forge;

import net.caffeinemc.mods.lithium.common.services.PlatformEntityAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.function.Predicate;

public class ForgeEntityAccess implements PlatformEntityAccess {

    @Override
    public void addEnderDragonParts(Level level, Entity excludedEntity, AABB box, Predicate<? super Entity> entityFilter, ArrayList<Entity> entities) {
        // NeoForge widened dragonParts() to its own PartEntity so that other mods can add multipart
        // entities; on Forge 26.2 the method still returns vanilla's EnderDragonPart, so only the
        // dragon's own parts need handling here.
        for (EnderDragonPart part : level.dragonParts()) {
            if (part != excludedEntity
                    && part.getParent() != excludedEntity
                    && entityFilter.test(part)
                    && box.intersects(part.getBoundingBox())
            ) {
                entities.add(part);
            }
        }
    }
}
