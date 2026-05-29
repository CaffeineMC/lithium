package net.caffeinemc.mods.lithium.common.hopper;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.caffeinemc.mods.lithium.api.inventory.LithiumInventory;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeEmitter;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracker;
import net.caffeinemc.mods.lithium.mixin.block.hopper.CompoundContainerAccessor;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class LithiumDoubleInventory extends CompoundContainer implements LithiumInventory, InventoryChangeTracker, InventoryChangeEmitter, InventoryChangeListener, ComparatorTracker {

    private final LithiumInventory first;
    private final LithiumInventory second;

    private LithiumStackList doubleStackList;

    ReferenceOpenHashSet<InventoryChangeListener> inventoryChangeListeners = null;
    ReferenceOpenHashSet<InventoryChangeListener> inventoryHandlingTypeListeners = null;

    /**
     * This method returns the same LithiumDoubleInventory instance for equal (same children in same order)
     * doubleInventory parameters until {@link #lithium$emitRemoved()} is called. After that a new LithiumDoubleInventory object
     * may be in use.
     *
     * @param doubleInventory A double inventory
     * @return The only non-removed LithiumDoubleInventory instance for the double inventory. Null if not compatible
     */
    public static LithiumDoubleInventory getLithiumInventory(CompoundContainer doubleInventory) {
        Container vanillaFirst = ((CompoundContainerAccessor) doubleInventory).getFirst();
        Container vanillaSecond = ((CompoundContainerAccessor) doubleInventory).getSecond();
        if (vanillaFirst != vanillaSecond && vanillaFirst instanceof LithiumInventory first && vanillaSecond instanceof LithiumInventory second) {
            LithiumDoubleStackList doubleStackList = LithiumDoubleStackList.getOrCreate(
                    first, second,
                    InventoryHelper.getLithiumStackList(first),
                    InventoryHelper.getLithiumStackList(second)
            );
            return doubleStackList.doubleInventory;
        }
        return null;
    }

    LithiumDoubleInventory(LithiumInventory first, LithiumInventory second) {
        super(first, second);
        this.first = first;
        this.second = second;
    }

    @Override
    public void lithium$emitContentModified() {
        ReferenceOpenHashSet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null) {
            for (InventoryChangeListener inventoryChangeListener : inventoryChangeListeners) {
                inventoryChangeListener.lithium$handleInventoryContentModified(this);
            }
            inventoryChangeListeners.clear();
        }
    }

    @Override
    public void lithium$emitStackListReplaced() {
        this.invalidateChangeListening();
    }

    @Override
    public void lithium$emitRemoved() {
        this.invalidateChangeListening();
    }


    private void invalidateChangeListening() {
        //Invalidate listeners to this inventory
        ReferenceOpenHashSet<InventoryChangeListener> listeners = this.inventoryHandlingTypeListeners;
        this.inventoryHandlingTypeListeners = null; //Prevent concurrent modification
        if (listeners != null && !listeners.isEmpty()) {
            listeners.forEach(listener -> listener.lithium$handleInventoryRemoved(this));
            listeners.clear();
            this.inventoryHandlingTypeListeners = listeners;
        }

        if (this.inventoryChangeListeners != null) {
            this.inventoryChangeListeners.clear();
        }

        //Invalidate own listening
        ((InventoryChangeTracker) this.first).stopListenForMajorInventoryChanges(this);
        ((InventoryChangeTracker) this.second).stopListenForMajorInventoryChanges(this);

        LithiumStackList lithiumStackList = this.doubleStackList;
        if (lithiumStackList != null) {
            lithiumStackList.removeInventoryModificationCallback(this);
        }
    }

    @Override
    public void lithium$emitFirstComparatorAdded() {
        ReferenceOpenHashSet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null && !inventoryChangeListeners.isEmpty()) {
            inventoryChangeListeners.removeIf(inventoryChangeListener -> inventoryChangeListener.lithium$handleComparatorAdded(this));
        }
    }

    @Override
    public void lithium$forwardContentChangeOnce(InventoryChangeListener inventoryChangeListener, LithiumStackList stackList) {
        if (this.inventoryChangeListeners == null) {
            this.inventoryChangeListeners = new ReferenceOpenHashSet<>(1);
        }
        if (this.inventoryChangeListeners.isEmpty()) {
            ((InventoryChangeTracker) this.first).listenForContentChangesOnce(InventoryHelper.getLithiumStackList(this.first), this);
            ((InventoryChangeTracker) this.second).listenForContentChangesOnce(InventoryHelper.getLithiumStackList(this.second), this);
        }
        this.inventoryChangeListeners.add(inventoryChangeListener);
    }

    @Override
    public void lithium$forwardMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners == null) {
            this.inventoryHandlingTypeListeners = new ReferenceOpenHashSet<>(1);
        }
        if (this.inventoryHandlingTypeListeners.isEmpty()) {
            ((InventoryChangeTracker) this.first).listenForMajorInventoryChanges(this);
            ((InventoryChangeTracker) this.second).listenForMajorInventoryChanges(this);
        }
        this.inventoryHandlingTypeListeners.add(inventoryChangeListener);
    }

    @Override
    public void lithium$stopForwardingMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners != null) {
            this.inventoryHandlingTypeListeners.remove(inventoryChangeListener);
            if (this.inventoryHandlingTypeListeners.isEmpty()) {
                ((InventoryChangeTracker) this.first).stopListenForMajorInventoryChanges(this);
                ((InventoryChangeTracker) this.second).stopListenForMajorInventoryChanges(this);
            }
        }
    }

    @Override
    public NonNullList<ItemStack> getInventoryLithium() {
        return this.doubleStackList;
    }

    @Override
    public void setInventoryLithium(NonNullList<ItemStack> inventory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void lithium$handleInventoryContentModified(Container inventory) {
        this.lithium$emitContentModified();
    }

    @Override
    public void lithium$handleInventoryRemoved(Container inventory) {
        this.lithium$emitRemoved();
    }

    @Override
    public boolean lithium$handleComparatorAdded(Container inventory) {
        this.lithium$emitFirstComparatorAdded();
        return this.inventoryChangeListeners.isEmpty();
    }

    @Override
    public void lithium$onComparatorAdded(Direction direction, int offset) {
        throw new UnsupportedOperationException("Call onComparatorAdded(Direction direction, int offset) on the inventory half only!");
    }

    @Override
    public boolean lithium$hasAnyComparatorNearby() {
        return ((ComparatorTracker) this.first).lithium$hasAnyComparatorNearby() || ((ComparatorTracker) this.second).lithium$hasAnyComparatorNearby();
    }

    public void setDoubleStackList(LithiumStackList doubleStackList) {
        if (this.doubleStackList != null) {
            throw new IllegalStateException("DoubleStackList already set!");
        }
        this.doubleStackList = doubleStackList;
    }
}
