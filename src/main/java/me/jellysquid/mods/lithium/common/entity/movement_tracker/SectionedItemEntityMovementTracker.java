package me.jellysquid.mods.lithium.common.entity.movement_tracker;

import me.jellysquid.mods.lithium.common.util.tuples.WorldSectionBox;
import me.jellysquid.mods.lithium.mixin.block.hopper.EntityTrackingSectionAccessor;
import me.jellysquid.mods.lithium.mixin.util.entity_movement_tracking.ServerEntityManagerAccessor;
import me.jellysquid.mods.lithium.mixin.util.entity_movement_tracking.ServerWorldAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.TypeFilterableList;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public class SectionedItemEntityMovementTracker<S extends Entity> extends SectionedEntityMovementTracker<Entity, S> {

    public SectionedItemEntityMovementTracker(WorldSectionBox worldSectionBox, Class<S> clazz) {
        super(worldSectionBox, clazz);
    }

    public static <S extends Entity> SectionedItemEntityMovementTracker<S> registerAt(ServerWorld world, Box encompassingBox, Class<S> clazz) {
        MovementTrackerCache cache = (MovementTrackerCache) ((ServerEntityManagerAccessor<?>) ((ServerWorldAccessor) world).getEntityManager()).getCache();

        WorldSectionBox worldSectionBox = WorldSectionBox.entityAccessBox(world, encompassingBox);
        SectionedItemEntityMovementTracker<S> tracker = new SectionedItemEntityMovementTracker<>(worldSectionBox, clazz);
        tracker = cache.deduplicate(tracker);

        tracker.register(world);
        return tracker;
    }

    public List<S> getEntities(Box area) {
        List<S> entities = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < this.sortedSections.size(); sectionIndex++) {
            if (this.sectionVisible[sectionIndex]) {
                //noinspection unchecked
                TypeFilterableList<S> collection = ((EntityTrackingSectionAccessor<S>) this.sortedSections.get(sectionIndex)).getCollection();

                for (S entity : collection.getAllOfType(this.clazz)) {
                    if (entity.isAlive()) {
                        Box entityBoundingBox = entity.getBoundingBox();
                        if (entityBoundingBox.intersects(area)) {
                            entities.add(entity);
                        }
                    }
                }
            }
        }
        return entities;
    }
}
