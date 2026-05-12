package net.caffeinemc.mods.lithium.common.hopper;

import net.caffeinemc.mods.lithium.api.inventory.LithiumTransferConditionInventory;
import net.caffeinemc.mods.lithium.common.util.DirectionConstants;
import net.caffeinemc.mods.lithium.common.world.WorldHelper;
import net.caffeinemc.mods.lithium.common.world.blockentity.BlockEntityGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HopperHelper {

    public static boolean tryMoveSingleItem(Container to, ItemStack stack, @Nullable Direction fromDirection) {
        ItemStack transferChecker;
        if (((LithiumTransferConditionInventory) to).lithium$itemInsertionTestRequiresStackSize1()) {
            transferChecker = stack.copy();
            transferChecker.setCount(1);
        } else {
            transferChecker = stack;
        }

        WorldlyContainer toSided = to instanceof WorldlyContainer ? ((WorldlyContainer) to) : null;
        if (toSided != null && fromDirection != null) {
            int[] slots = toSided.getSlotsForFace(fromDirection);

            for(int slotIndex = 0; slotIndex < slots.length; ++slotIndex) {
                if (tryMoveSingleItem(to, toSided, stack, transferChecker, slots[slotIndex], fromDirection)) {
                    return true; //caller needs to take the item from the original inventory and call to.markDirty()
                }
            }
        } else {
            int j = to.getContainerSize();
            for(int slot = 0; slot < j; ++slot) {
                if (tryMoveSingleItem(to, toSided, stack, transferChecker, slot, fromDirection)) {
                    return true; //caller needs to take the item from the original inventory and call to.markDirty()
                }
            }
        }
        return false;
    }

    public static boolean tryMoveSingleItem(Container to, @Nullable WorldlyContainer toSided, ItemStack transferStack, ItemStack transferChecker, int targetSlot, @Nullable Direction fromDirection) {
        ItemStack toStack = to.getItem(targetSlot);
        if (to.canPlaceItem(targetSlot, transferChecker) && (toSided == null || toSided.canPlaceItemThroughFace(targetSlot, transferChecker, fromDirection))) {
            int toCount;
            if (toStack.isEmpty()) {
                ItemStack singleItem = transferStack.split(1);
                to.setItem(targetSlot, singleItem);
                return true; //caller needs to call to.markDirty()
            } else if (toStack.getMaxStackSize() > (toCount = toStack.getCount()) && to.getMaxStackSize() > toCount && ItemStack.isSameItemSameComponents(toStack, transferStack)) {
                transferStack.shrink(1);
                toStack.grow(1);
                return true; //caller needs to call to.markDirty()
            }
        }
        return false;
    }

    private static int calculateReducedSignalStrength(float contentWeight, int inventorySize, int inventoryMaxCountPerStack, int numOccupiedSlots, int itemStackCount, int itemStackMaxCount) {
        //contentWeight adaption can include rounding error for non-power of 2 max stack sizes, which do not exist in vanilla anyways
        int maxStackSize = Math.min(inventoryMaxCountPerStack, itemStackMaxCount);
        int newNumOccupiedSlots = numOccupiedSlots - (itemStackCount == 1 ? 1 : 0);
        float newContentWeight = contentWeight - (1f / (float) maxStackSize);
        newContentWeight /= (float) inventorySize;
        return Mth.floor(newContentWeight * 14.0F) + (newNumOccupiedSlots > 0 ? 1 : 0);
    }

    public static ComparatorUpdatePattern determineComparatorUpdatePattern(Container from, LithiumStackList fromStackList) {
        if ((from instanceof HopperBlockEntity) || !(from instanceof BaseContainerBlockEntity)) {
            return ComparatorUpdatePattern.NO_UPDATE;
        }
        //calculate the signal strength of the inventory, but also keep the content weight variable
        float contentWeight = 0f;
        int numOccupiedSlots = 0;

        for (int j = 0; j < from.getContainerSize(); ++j) {
            ItemStack itemStack = from.getItem(j);
            if (!itemStack.isEmpty()) {
                int maxStackSize = Math.min(from.getMaxStackSize(), itemStack.getMaxStackSize());
                contentWeight += itemStack.getCount() / (float) maxStackSize;
                ++numOccupiedSlots;
            }
        }
        float f = contentWeight;
        f /= (float)from.getContainerSize();
        int originalSignalStrength = Mth.floor(f * 14.0F) + (numOccupiedSlots > 0 ? 1 : 0);


        ComparatorUpdatePattern updatePattern = ComparatorUpdatePattern.NO_UPDATE;
        //check the signal strength change when failing to extract from each slot
        int[] availableSlots = from instanceof WorldlyContainer ? ((WorldlyContainer) from).getSlotsForFace(Direction.DOWN) : null;
        WorldlyContainer sidedInventory = from instanceof WorldlyContainer ? (WorldlyContainer) from : null;
        int fromSize = availableSlots != null ? availableSlots.length : from.getContainerSize();
        for (int i = 0; i < fromSize; i++) {
            int fromSlot = availableSlots != null ? availableSlots[i] : i;
            ItemStack itemStack = fromStackList.get(fromSlot);
            if (!itemStack.isEmpty() && (sidedInventory == null || sidedInventory.canTakeItemThroughFace(fromSlot, itemStack, Direction.DOWN))) {
                int newSignalStrength = calculateReducedSignalStrength(contentWeight, from.getContainerSize(), from.getMaxStackSize(), numOccupiedSlots, itemStack.getCount(), itemStack.getMaxStackSize());
                if (newSignalStrength != originalSignalStrength) {
                    updatePattern = updatePattern.thenDecrementUpdateIncrementUpdate();
                } else {
                    updatePattern = updatePattern.thenUpdate();
                }
                if (!updatePattern.isChainable()) {
                    break; //if the pattern is indistinguishable from all extensions of the pattern, stop iterating
                }
            }
        }
        return updatePattern;
    }

    public static Container replaceDoubleInventory(Container blockInventory) {
        if (blockInventory instanceof CompoundContainer doubleInventory) {
            doubleInventory = LithiumDoubleInventory.getLithiumInventory(doubleInventory);
            if (doubleInventory != null) {
                return doubleInventory;
            }
        }
        return blockInventory;
    }

    public static void updateHopperOnUpdateSuppression(Level level, BlockPos pos, int flags, LevelChunk worldChunk, boolean stateChange) {
        if ((flags & Block.UPDATE_NEIGHBORS) == 0 && stateChange) {
            //No block updates were sent. We need to update nearby hoppers to avoid outdated inventory caches being used

            //Small performance improvement when getting block entities within the same chunk.
            Map<BlockPos, BlockEntity> blockEntities = WorldHelper.areNeighborsWithinSameChunk(pos) ? worldChunk.getBlockEntities() : null;
            if (blockEntities == null || !blockEntities.isEmpty()) {
                for (Direction direction : DirectionConstants.ALL) {
                    BlockPos offsetPos = pos.relative(direction);
                    //Directly get the block entity instead of getting the block state first. Maybe that is faster, maybe not.
                    BlockEntity hopper = blockEntities != null ? blockEntities.get(offsetPos) : ((BlockEntityGetter) level).lithium$getLoadedExistingBlockEntity(offsetPos);
                    if (hopper instanceof UpdateReceiver updateReceiver) {
                        updateReceiver.lithium$invalidateCacheOnNeighborUpdate(direction == Direction.DOWN);
                    }
                }
            }
        }
    }
}
