package net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking;

import net.caffeinemc.mods.lithium.common.hopper.LithiumStackList;

public interface InventoryChangeTracker extends InventoryChangeEmitter {
    default void listenForContentChangesOnce(LithiumStackList stackList, InventoryChangeListener inventoryChangeListener) {
        this.lithium$forwardContentChangeOnce(inventoryChangeListener, stackList);
    }

    default void listenForMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        this.lithium$forwardMajorInventoryChanges(inventoryChangeListener);
    }

    default void stopListenForMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        this.lithium$stopForwardingMajorInventoryChanges(inventoryChangeListener);
    }
}
