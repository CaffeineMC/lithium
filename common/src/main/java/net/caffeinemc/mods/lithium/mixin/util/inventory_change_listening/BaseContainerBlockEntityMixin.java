package net.caffeinemc.mods.lithium.mixin.util.inventory_change_listening;


import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.caffeinemc.mods.lithium.api.inventory.LithiumInventory;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeEmitter;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import net.caffeinemc.mods.lithium.common.hopper.InventoryHelper;
import net.caffeinemc.mods.lithium.common.hopper.LithiumStackList;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin implements InventoryChangeEmitter, Container {
    @Unique
    ReferenceArraySet<InventoryChangeListener> inventoryChangeListeners = null;
    @Unique
    ReferenceArraySet<InventoryChangeListener> inventoryHandlingTypeListeners = null;

    @Override
    public void lithium$emitContentModified() {
        ReferenceArraySet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null) {
            for (InventoryChangeListener inventoryChangeListener : inventoryChangeListeners) {
                inventoryChangeListener.lithium$handleInventoryContentModified(this);
            }
            inventoryChangeListeners.clear();
        }
    }

    @Override
    public void lithium$emitStackListReplaced() {
        ReferenceArraySet<InventoryChangeListener> listeners = this.inventoryHandlingTypeListeners;
        this.inventoryHandlingTypeListeners = null; //Prevent concurrent modification
        if (listeners != null && !listeners.isEmpty()) {
            for (InventoryChangeListener inventoryChangeListener : listeners) {
                inventoryChangeListener.handleStackListReplaced(this);
            }
            listeners.clear();
        }
        if (this.inventoryHandlingTypeListeners == null) {
            this.inventoryHandlingTypeListeners = listeners;
        }

        if (this instanceof InventoryChangeListener listener) {
            listener.handleStackListReplaced(this);
        }

        this.invalidateChangeListening();
    }

    @Override
    public void lithium$emitRemoved() {
        ReferenceArraySet<InventoryChangeListener> listeners = this.inventoryHandlingTypeListeners;
        this.inventoryHandlingTypeListeners = null; //Prevent concurrent modification
        if (listeners != null && !listeners.isEmpty()) {
            for (InventoryChangeListener listener : listeners) {
                listener.lithium$handleInventoryRemoved(this);
            }
            listeners.clear();
        }
        if (this.inventoryHandlingTypeListeners == null) {
            this.inventoryHandlingTypeListeners = listeners;
        }

        if (this instanceof InventoryChangeListener listener) {
            listener.lithium$handleInventoryRemoved(this);
        }

        this.invalidateChangeListening();
    }

    @Unique
    private void invalidateChangeListening() {
        if (this.inventoryChangeListeners != null) {
            this.inventoryChangeListeners.clear();
        }

        LithiumStackList lithiumStackList = this instanceof LithiumInventory ? InventoryHelper.getLithiumStackListOrNull((LithiumInventory) this) : null;
        if (lithiumStackList != null && this instanceof InventoryChangeTracker inventoryChangeTracker) {
            lithiumStackList.removeInventoryModificationCallback(inventoryChangeTracker);
        }
    }

    @Override
    public void lithium$emitFirstComparatorAdded() {
        ReferenceArraySet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null && !inventoryChangeListeners.isEmpty()) {
            inventoryChangeListeners.removeIf(inventoryChangeListener -> inventoryChangeListener.lithium$handleComparatorAdded(this));
        }
    }

    @Override
    public void lithium$forwardContentChangeOnce(InventoryChangeListener inventoryChangeListener, LithiumStackList stackList) {
        if (this.inventoryChangeListeners == null) {
            this.inventoryChangeListeners = new ReferenceArraySet<>(1);
        }
        stackList.setNextInventoryModificationCallback((InventoryChangeTracker) this);
        this.inventoryChangeListeners.add(inventoryChangeListener);

    }

    @Override
    public void lithium$forwardMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners == null) {
            this.inventoryHandlingTypeListeners = new ReferenceArraySet<>(1);
        }
        this.inventoryHandlingTypeListeners.add(inventoryChangeListener);
    }

    @Override
    public void lithium$stopForwardingMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners != null) {
            this.inventoryHandlingTypeListeners.remove(inventoryChangeListener);
        }
    }
}
