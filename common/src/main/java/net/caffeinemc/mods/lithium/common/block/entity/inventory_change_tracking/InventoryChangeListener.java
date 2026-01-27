package net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking;

import net.minecraft.world.Container;

public interface InventoryChangeListener {
    default void handleStackListReplaced(Container inventory) {
        this.lithium$handleInventoryRemoved(inventory);
    }

    void lithium$handleInventoryContentModified(Container inventory);

    void lithium$handleInventoryRemoved(Container inventory);

    /**
     * Propagates an update (comparator added in inventory range)
     *
     * @param inventory the inventory the update is coming from
     * @return Whether the listener unsubscribes due to this update
     */
    boolean lithium$handleComparatorAdded(Container inventory);
}
