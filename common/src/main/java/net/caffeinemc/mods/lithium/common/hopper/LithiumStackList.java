package net.caffeinemc.mods.lithium.common.hopper;

import net.caffeinemc.mods.lithium.api.inventory.LithiumDefaultedList;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import net.caffeinemc.mods.lithium.common.util.change_tracking.ChangePublisher;
import net.caffeinemc.mods.lithium.common.util.change_tracking.ChangeSubscriber;
import net.caffeinemc.mods.lithium.mixin.block.hopper.NonNullListAccessor;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LithiumStackList extends NonNullList<ItemStack> implements LithiumDefaultedList, ChangeSubscriber.CountChangeSubscriber<ItemStack> {
    final int maxCountPerStack;

    protected int cachedSignalStrength;
    private ComparatorUpdatePattern cachedComparatorUpdatePattern;

    private boolean signalStrengthOverride;

    private long modCount;
    private int occupiedSlots;
    private int fullSlots;

    LithiumDoubleStackList parent; //only used for double chests

    InventoryChangeTracker nextInventoryModificationCallback;

    public LithiumStackList(NonNullList<ItemStack> original, int maxCountPerStack) {
        //noinspection unchecked
        super(((NonNullListAccessor<ItemStack>) original).getDelegate(), ItemStack.EMPTY);
        this.maxCountPerStack = maxCountPerStack;

        this.cachedSignalStrength = -1;
        this.cachedComparatorUpdatePattern = null;
        this.modCount = 0;
        this.signalStrengthOverride = false;

        this.occupiedSlots = 0;
        this.fullSlots = 0;
        int size = this.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = this.get(i);
            if (!stack.isEmpty()) {
                this.occupiedSlots++;
                if (stack.getMaxStackSize() <= stack.getCount()) {
                    this.fullSlots++;
                }
                //noinspection unchecked
                ((ChangePublisher<ItemStack>) (Object) stack).lithium$subscribe(this, i);
            }
        }

        this.nextInventoryModificationCallback = null;
    }

    public LithiumStackList(int maxCountPerStack) {
        super(null, ItemStack.EMPTY);
        this.maxCountPerStack = maxCountPerStack;
        this.cachedSignalStrength = -1;
        this.nextInventoryModificationCallback = null;
    }
    public long getModCount() {
        return this.modCount;
    }

    public void changedALot() {
        this.changed();

        //fix the slot mapping of all stacks in the inventory
        //fix occupied/full slot counters
        this.occupiedSlots = 0;
        this.fullSlots = 0;
        int size = this.size();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            ItemStack stack = this.get(i);
            if (!stack.isEmpty()) {
                this.occupiedSlots++;
                if (stack.getMaxStackSize() <= stack.getCount()) {
                    this.fullSlots++;
                }
                //noinspection unchecked
                ((ChangePublisher<ItemStack>) (Object) stack).lithium$unsubscribe(this);
            }
        }
        for (int i = 0; i < size; i++) {
            ItemStack stack = this.get(i);
            if (!stack.isEmpty()) {
                //noinspection unchecked
                ((ChangePublisher<ItemStack>) (Object) stack).lithium$subscribe(this, i);
            }
        }

    }

    /**
     * Method that must be invoked before or after a change of the inventory to update important values. If done too
     * early or too late, behavior might be incorrect.
     */
    public void changed() {
        this.cachedSignalStrength = -1;
        this.cachedComparatorUpdatePattern = null;
        this.modCount++;

        InventoryChangeTracker inventoryModificationCallback = this.nextInventoryModificationCallback;
        if (inventoryModificationCallback != null) {
            this.nextInventoryModificationCallback = null;
            inventoryModificationCallback.lithium$emitContentModified();
        }
    }

    @Override
    public ItemStack set(int index, ItemStack element) {
        ItemStack previous = super.set(index, element);

        //Handle vanilla's item stack resurrection in HopperBlockEntity extract(Hopper hopper, Inventory inventory, int slot, Direction side):
        // Item stacks are set to 0 items, then back to 1. Then inventory.set(index, element) is called.
        // At this point, the LithiumStackList unsubscribed from the stack when it reached 0.
        // Handle: If the previous == element, and the stack is not subscribed, we handle it as if an empty stack was replaced.
        if (previous == element && !element.isEmpty()) {
            //noinspection unchecked
            boolean notSubscribed = ((ChangePublisher<ItemStack>) (Object) previous).lithium$isSubscribedWithData(this, index);
            if (!notSubscribed)  {
                previous = ItemStack.EMPTY;
            }
        }

        if (previous != element) {
            if (!previous.isEmpty()) {
                //noinspection unchecked
                ((ChangePublisher<ItemStack>) (Object) previous).lithium$unsubscribeWithData(this, index);
            }
            if (!element.isEmpty()) {
                //noinspection unchecked
                ((ChangePublisher<ItemStack>) (Object) element).lithium$subscribe(this, index);
            }

            this.occupiedSlots += (previous.isEmpty() ? 1 : 0) - (element.isEmpty() ? 1 : 0);
            this.fullSlots += (element.getCount() >= element.getMaxStackSize() ? 1 : 0) - (previous.getCount() >= previous.getMaxStackSize() ? 1 : 0);
            this.changed();
        }

        return previous;
    }

    @Override
    public void add(int slot, ItemStack element) {
        super.add(slot, element);
        if (!element.isEmpty()) {
            //noinspection unchecked
            ((ChangePublisher<ItemStack>) (Object) element).lithium$subscribe(this, this.indexOf(element));
        }
        this.changedALot();
    }

    @Override
    public ItemStack remove(int index) {
        ItemStack previous = super.remove(index);
        if (!previous.isEmpty()) {
            //noinspection unchecked
            ((ChangePublisher<ItemStack>) (Object) previous).lithium$unsubscribeWithData(this, index);
        }
        this.changedALot();
        return previous;
    }

    @Override
    public void clear() {
        int size = this.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = this.get(i);
            if (!stack.isEmpty()) {
                //noinspection unchecked
                ((ChangePublisher<ItemStack>) (Object) stack).lithium$unsubscribeWithData(this, i);
            }
        }
        super.clear();
        this.changedALot();
    }

    public boolean hasSignalStrengthOverride() {
        return this.signalStrengthOverride;
    }

    public int getSignalStrength(Container inventory) {
        if (this.signalStrengthOverride) {
            return 0;
        }
        int signalStrength = this.cachedSignalStrength;
        if (signalStrength == -1) {
            return this.cachedSignalStrength = this.calculateSignalStrength(inventory.getContainerSize());
        }
        return signalStrength;
    }

    /**
     * [VanillaCopy] {@link net.minecraft.world.inventory.AbstractContainerMenu#getRedstoneSignalFromContainer(Container)}
     *
     * @return the signal strength for this inventory
     */
    int calculateSignalStrength(int inventorySize) {
        int i = 0;
        float f = 0.0F;

        inventorySize = Math.min(inventorySize, this.size());
        for (int j = 0; j < inventorySize; ++j) {
            ItemStack itemStack = this.get(j);
            if (!itemStack.isEmpty()) {
                f += (float) itemStack.getCount() / (float) Math.min(this.maxCountPerStack, itemStack.getMaxStackSize());
                ++i;
            }
        }

        f /= (float) inventorySize;
        return Mth.floor(f * 14.0F) + (i > 0 ? 1 : 0);
    }

    public void setReducedSignalStrengthOverride() {
        this.signalStrengthOverride = true;
    }

    public void clearSignalStrengthOverride() {
        this.signalStrengthOverride = false;
    }


    /**
     * @param masterStackList the stacklist of the inventory that comparators read from (double inventory for double chests)
     * @param inventory       the blockentity / inventory that this stacklist is inside
     */
    public void runComparatorUpdatePatternOnFailedExtract(LithiumStackList masterStackList, Container inventory) {
        if (inventory instanceof BlockEntity) {
            if (this.cachedComparatorUpdatePattern == null) {
                this.cachedComparatorUpdatePattern = HopperHelper.determineComparatorUpdatePattern(inventory, masterStackList);
            }
            this.cachedComparatorUpdatePattern.apply((BlockEntity) inventory, masterStackList);
        }
    }

    public boolean maybeSendsComparatorUpdatesOnFailedExtract() {
        return this.cachedComparatorUpdatePattern == null || this.cachedComparatorUpdatePattern != ComparatorUpdatePattern.NO_UPDATE;
    }

    public int getOccupiedSlots() {
        return this.occupiedSlots;
    }

    public int getFullSlots() {
        return this.fullSlots;
    }

    @Override
    public void changedInteractionConditions() {
        this.changed();
    }


    /**
     * Links the stack list to the corresponding block entity to propagate the next inventory change
     */
    public void setNextInventoryModificationCallback(@NotNull InventoryChangeTracker nextInventoryModificationCallback) {
        if (this.nextInventoryModificationCallback != null && this.nextInventoryModificationCallback != nextInventoryModificationCallback) {
            this.nextInventoryModificationCallback.emitCallbackReplaced();
        }
        this.nextInventoryModificationCallback = nextInventoryModificationCallback;
    }

    public void removeInventoryModificationCallback(@NotNull InventoryChangeTracker inventoryModificationCallback) {
        if (this.nextInventoryModificationCallback != null && this.nextInventoryModificationCallback == inventoryModificationCallback) {
            this.nextInventoryModificationCallback = null;
        }
    }

    @Override
    public void lithium$notify(@Nullable ItemStack publisher, int subscriberData) {
        //Item component changes: LithiumStackList does not care about this
    }

    @Override
    public void lithium$forceUnsubscribe(ItemStack publisher, int subscriberData) {
        throw new UnsupportedOperationException("Cannot force unsubscribe on a LithiumStackList!");
    }

    @Override
    public void lithium$notifyCount(ItemStack stack, int index, int newCount) {
        assert stack ==  this.get(index);
        int count = stack.getCount();
        if (newCount <= 0) {
            //noinspection unchecked
            ((ChangePublisher<ItemStack>) (Object) stack).lithium$unsubscribeWithData(this, index);
        }
        int maxCount = stack.getMaxStackSize();
        this.occupiedSlots -= newCount <= 0 ? 1 : 0;
        this.fullSlots += (newCount >= maxCount ? 1 : 0) - (count >= maxCount ? 1 : 0);

        this.changed();
    }
}
