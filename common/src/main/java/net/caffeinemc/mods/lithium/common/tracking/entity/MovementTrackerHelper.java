package net.caffeinemc.mods.lithium.common.tracking.entity;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.caffeinemc.mods.lithium.api.inventory.LithiumInventory;
import net.caffeinemc.mods.lithium.common.entity.EntityClassGroup;
import net.caffeinemc.mods.lithium.common.entity.movement.CollisionEntityClassGroups;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.entity.EntityAccess;

import java.util.List;

/**
 * Helps to track in which entity sections entities of a certain type moved, appeared or disappeared by providing int
 * masks for all Entity classes.
 * Helps to track the entities within a world and provide notifications to listeners when a tracked entity enters or leaves a
 * watched area. This removes the necessity to constantly poll the world for nearby entities each tick and generally
 * provides a sizable boost to performance of hoppers.
 */
public abstract class MovementTrackerHelper {
    public static final List<Class<?>> MOVEMENT_NOTIFYING_ENTITY_CLASSES;
    public static final List<EntityClassGroup> MOVEMENT_NOTIFYING_ENTITY_CLASS_GROUPS;
    public static volatile Reference2IntOpenHashMap<Class<? extends EntityAccess>> CLASS_2_NOTIFY_MASK;
    public static final int NUM_MOVEMENT_NOTIFYING_CLASSES;

    static {
        if (LithiumInventory.class.isAssignableFrom(HopperBlockEntity.class)) {
            MOVEMENT_NOTIFYING_ENTITY_CLASSES = List.of(ItemEntity.class, Container.class);
        } else {
            MOVEMENT_NOTIFYING_ENTITY_CLASSES = List.of();
        }
        if (SectionedColliderEntityMovementTracker.ENABLED) {
            MOVEMENT_NOTIFYING_ENTITY_CLASS_GROUPS = List.of(CollisionEntityClassGroups.BOAT_SHULKER_LIKE_COLLISION);
        } else {
            MOVEMENT_NOTIFYING_ENTITY_CLASS_GROUPS = List.of();
        }

        CLASS_2_NOTIFY_MASK = new Reference2IntOpenHashMap<>();
        CLASS_2_NOTIFY_MASK.defaultReturnValue(-1);
        NUM_MOVEMENT_NOTIFYING_CLASSES = MOVEMENT_NOTIFYING_ENTITY_CLASSES.size() + MOVEMENT_NOTIFYING_ENTITY_CLASS_GROUPS.size();
    }

    public static int getNotificationMask(Entity entity) {
        int notificationMask = CLASS_2_NOTIFY_MASK.getInt(entity.getClass());
        if (notificationMask == -1) {
            notificationMask = calculateNotificationMask(entity);
        }
        return notificationMask;
    }

    private static int calculateNotificationMask(Entity entity) {
        int mask = 0;
        Class<? extends Entity> entityClass = entity.getClass();
        for (int i = 0; i < MOVEMENT_NOTIFYING_ENTITY_CLASSES.size(); i++) {
            Class<?> superclass = MOVEMENT_NOTIFYING_ENTITY_CLASSES.get(i);
            if (superclass.isAssignableFrom(entityClass)) {
                mask |= 1 << i;
            }
        }
        for (int i = 0; i < MOVEMENT_NOTIFYING_ENTITY_CLASS_GROUPS.size(); i++) {
            EntityClassGroup group = MOVEMENT_NOTIFYING_ENTITY_CLASS_GROUPS.get(i);
            if (group.contains(entity)) {
                mask |= 1 << (i + MOVEMENT_NOTIFYING_ENTITY_CLASSES.size());
            }
        }

        //progress can be lost here, but it can only cost performance
        //copy on write followed by publication in volatile field guarantees visibility of the final state
        Reference2IntOpenHashMap<Class<? extends EntityAccess>> copy = CLASS_2_NOTIFY_MASK.clone();
        copy.put(entityClass, mask);
        CLASS_2_NOTIFY_MASK = copy;

        return mask;
    }

    static int getTrackerIndex(Object classOrClassGroup) {
        if (classOrClassGroup instanceof Class<?>) {
            return MOVEMENT_NOTIFYING_ENTITY_CLASSES.indexOf(classOrClassGroup);
        } else if (classOrClassGroup instanceof EntityClassGroup) {
            return MOVEMENT_NOTIFYING_ENTITY_CLASSES.size() + MOVEMENT_NOTIFYING_ENTITY_CLASS_GROUPS.indexOf(classOrClassGroup);
        }
        return -1;
    }
}
