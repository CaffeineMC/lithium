package net.caffeinemc.mods.lithium.mixin.block.hopper;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.api.inventory.LithiumCooldownReceivingInventory;
import net.caffeinemc.mods.lithium.api.inventory.LithiumInventory;
import net.caffeinemc.mods.lithium.common.block.entity.SleepingBlockEntity;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracker;
import net.caffeinemc.mods.lithium.common.hopper.*;
import net.caffeinemc.mods.lithium.common.services.PlatformModCompat;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedEntityMovementListener;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedInventoryEntityMovementTracker;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedItemEntityMovementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static net.minecraft.world.level.block.entity.HopperBlockEntity.getItemsAtAndAbove;


@Mixin(value = HopperBlockEntity.class, priority = 950)
public abstract class HopperBlockEntityMixin extends BlockEntity implements Hopper, UpdateReceiver, LithiumInventory, InventoryChangeListener, SectionedEntityMovementListener {

    public HopperBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    protected abstract boolean isOnCustomCooldown();

    @Shadow
    private long tickedGameTime;

    private long myModCountAtLastInsert, myModCountAtLastExtract, myModCountAtLastItemCollect;

    private HopperCachingState.BlockInventory insertionMode = HopperCachingState.BlockInventory.UNKNOWN;
    private HopperCachingState.BlockInventory extractionMode = HopperCachingState.BlockInventory.UNKNOWN;

    //The currently used block inventories
    @Nullable
    private Container insertBlockInventory, extractBlockInventory;

    //The currently used inventories (optimized type, if not present, skip optimizations)
    @Nullable
    private LithiumInventory insertInventory, extractInventory;
    @Nullable //Null iff corresp. LithiumInventory field is null
    private LithiumStackList insertStackList, extractStackList;
    //Mod count used to avoid transfer attempts that are known to fail (no change since last attempt)
    private long insertStackListModCount, extractStackListModCount;

    private SectionedItemEntityMovementTracker<ItemEntity> collectItemEntityTracker;
    private boolean collectItemEntityTrackerWasEmpty;
    private AABB collectItemEntityBox;
    private long collectItemEntityAttemptTime;

    private SectionedInventoryEntityMovementTracker<Container> extractInventoryEntityTracker;
    private AABB extractInventoryEntityBox;
    private long extractInventoryEntityFailedSearchTime;

    private SectionedInventoryEntityMovementTracker<Container> insertInventoryEntityTracker;
    private AABB insertInventoryEntityBox;
    private long insertInventoryEntityFailedSearchTime;

    private boolean shouldCheckSleep;

    @Redirect(method = "suckInItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getSourceContainer(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/Container;"))
    private static Container getExtractInventory(Level world, Hopper hopper, BlockPos extractBlockPos, BlockState extractBlockState) {
        if (!(hopper instanceof HopperBlockEntityMixin hopperBlockEntity)) {
            return getSourceContainer(world, hopper, extractBlockPos, extractBlockState); //Hopper Minecarts do not cache Inventories
        }

        Container blockInventory = hopperBlockEntity.getExtractBlockInventory(world, extractBlockPos, extractBlockState);
        if (blockInventory != null) {
            return blockInventory;
        }

        if (hopperBlockEntity.extractInventoryEntityTracker == null) {
            hopperBlockEntity.initExtractInventoryTracker(world);
        }
        if (hopperBlockEntity.extractInventoryEntityTracker.isUnchangedSince(hopperBlockEntity.extractInventoryEntityFailedSearchTime)) {
            hopperBlockEntity.extractInventoryEntityFailedSearchTime = hopperBlockEntity.tickedGameTime;
            return null;
        }
        hopperBlockEntity.extractInventoryEntityFailedSearchTime = Long.MIN_VALUE;
        hopperBlockEntity.shouldCheckSleep = false;

        List<Container> inventoryEntities = hopperBlockEntity.extractInventoryEntityTracker.getEntities(hopperBlockEntity.extractInventoryEntityBox);
        if (inventoryEntities.isEmpty()) {
            hopperBlockEntity.extractInventoryEntityFailedSearchTime = hopperBlockEntity.tickedGameTime;
            //only set unchanged when no entity present. this allows shortcutting this case
            //shortcutting the entity present case requires checking its change counter
            return null;
        }
        Container inventory = inventoryEntities.get(world.random.nextInt(inventoryEntities.size()));
        if (inventory instanceof LithiumInventory optimizedInventory) {
            LithiumStackList extractInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
            if (inventory != hopperBlockEntity.extractInventory || hopperBlockEntity.extractStackList != extractInventoryStackList) {
                //not caching the inventory (NO_BLOCK_INVENTORY prevents it)
                //make change counting on the entity inventory possible, without caching it as block inventory
                hopperBlockEntity.cacheExtractLithiumInventory(optimizedInventory);
            }
        }
        return inventory;
    }

    /**
     * Effectively overwrites {@link HopperBlockEntity#ejectItems(Level, BlockPos, BlockState, Container)} (only usage redirect)
     * [VanillaCopy] general hopper insert logic, modified for optimizations
     *
     * @reason Adding the inventory caching into the static method using mixins seems to be unfeasible without temporarily storing state in static fields.
     */
    @SuppressWarnings("JavadocReference")
    @Inject(
            cancellable = true,
            method = "ejectItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Z",
            at = @At(
                    value = "INVOKE", shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;isFullContainer(Lnet/minecraft/world/Container;Lnet/minecraft/core/Direction;)Z"
            ), locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void lithiumInsert(Level world, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir, Container insertInventory, Direction direction) {
        if (insertInventory == null || !(blockEntity instanceof HopperBlockEntity) || blockEntity instanceof WorldlyContainer) {
            //call the vanilla code to allow other mods inject features
            //e.g. carpet mod allows hoppers to insert items into wool blocks
            return;
        }
        HopperBlockEntityMixin hopperBlockEntity = (HopperBlockEntityMixin) (Object) blockEntity;

        LithiumStackList hopperStackList = InventoryHelper.getLithiumStackList(hopperBlockEntity);
        if (hopperBlockEntity.insertInventory == insertInventory && hopperStackList.getModCount() == hopperBlockEntity.myModCountAtLastInsert) {
            if (hopperBlockEntity.insertStackList != null && hopperBlockEntity.insertStackList.getModCount() == hopperBlockEntity.insertStackListModCount) {
//                ComparatorUpdatePattern.NO_UPDATE.apply(hopperBlockEntity, hopperStackList); //commented because it's a noop, Hoppers do not send useless comparator updates
                cir.setReturnValue(false);
                return;
            }
        }

        boolean insertInventoryWasEmptyHopperNotDisabled = insertInventory instanceof HopperBlockEntityMixin &&
                !((HopperBlockEntityMixin) insertInventory).isOnCustomCooldown() && hopperBlockEntity.insertStackList != null &&
                hopperBlockEntity.insertStackList.getOccupiedSlots() == 0;

        boolean insertInventoryHandlesModdedCooldown =
                ((LithiumCooldownReceivingInventory) insertInventory).canReceiveTransferCooldown() &&
                        hopperBlockEntity.insertStackList != null ?
                        hopperBlockEntity.insertStackList.getOccupiedSlots() == 0 :
                        insertInventory.isEmpty();


        //noinspection ConstantConditions
        if (!(hopperBlockEntity.insertInventory == insertInventory && hopperBlockEntity.insertStackList.getFullSlots() == hopperBlockEntity.insertStackList.size())) {
            Direction fromDirection = hopperBlockEntity.facing.getOpposite();
            int size = hopperStackList.size();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < size; ++i) {
                ItemStack transferStack = hopperStackList.get(i);
                if (!transferStack.isEmpty()) {
                    boolean transferSuccess = HopperHelper.tryMoveSingleItem(insertInventory, transferStack, fromDirection);
                    if (transferSuccess) {
                        if (insertInventoryWasEmptyHopperNotDisabled) {
                            HopperBlockEntityMixin receivingHopper = (HopperBlockEntityMixin) insertInventory;
                            int k = 8;
                            if (receivingHopper.tickedGameTime >= hopperBlockEntity.tickedGameTime) {
                                k = 7;
                            }
                            receivingHopper.setCooldown(k);
                        }
                        if (insertInventoryHandlesModdedCooldown) {
                            ((LithiumCooldownReceivingInventory) insertInventory).setTransferCooldown(hopperBlockEntity.tickedGameTime);
                        }
                        insertInventory.setChanged();
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
        hopperBlockEntity.myModCountAtLastInsert = hopperStackList.getModCount();
        if (hopperBlockEntity.insertStackList != null) {
            hopperBlockEntity.insertStackListModCount = hopperBlockEntity.insertStackList.getModCount();
        }
        cir.setReturnValue(false);
    }

    /**
     * Inject to replace the extract method with an optimized but equivalent replacement.
     * Uses the vanilla method as fallback for non-optimized Inventories.
     *
     * @param to   Hopper or Hopper Minecart that is extracting
     * @param from Inventory the hopper is extracting from
     */
    @Inject(method = "suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/core/Direction;DOWN:Lnet/minecraft/core/Direction;", shift = At.Shift.AFTER), cancellable = true)
    private static void lithiumExtract(Level world, Hopper to, CallbackInfoReturnable<Boolean> cir, @Local Container from) {
        if (!(to instanceof HopperBlockEntityMixin hopperBlockEntity)) {
            return; //optimizations not implemented for hopper minecarts
        }
        if (from != hopperBlockEntity.extractInventory || hopperBlockEntity.extractStackList == null) {
            return; //from inventory is not an optimized inventory, vanilla fallback
        }

        LithiumStackList hopperStackList = InventoryHelper.getLithiumStackList(hopperBlockEntity);
        LithiumStackList fromStackList = hopperBlockEntity.extractStackList;

        if (hopperStackList.getModCount() == hopperBlockEntity.myModCountAtLastExtract) {
            if (fromStackList.getModCount() == hopperBlockEntity.extractStackListModCount) {
                if (!(from instanceof ComparatorTracker comparatorTracker) || comparatorTracker.lithium$hasAnyComparatorNearby()) {
                    //noinspection CollectionAddedToSelf
                    fromStackList.runComparatorUpdatePatternOnFailedExtract(fromStackList, from);
                }
                cir.setReturnValue(false);
                return;
            }
        }

        int[] availableSlots = from instanceof WorldlyContainer ? ((WorldlyContainer) from).getSlotsForFace(Direction.DOWN) : null;
        int fromSize = availableSlots != null ? availableSlots.length : from.getContainerSize();
        for (int i = 0; i < fromSize; i++) {
            int fromSlot = availableSlots != null ? availableSlots[i] : i;
            ItemStack itemStack = fromStackList.get(fromSlot);
            if (!itemStack.isEmpty() && canTakeItemFromContainer(to, from, itemStack, fromSlot, Direction.DOWN)) {
                //calling removeStack is necessary due to its side effects (markDirty in LootableContainerBlockEntity)
                ItemStack takenItem = from.removeItem(fromSlot, 1);
                assert !takenItem.isEmpty();
                boolean transferSuccess = HopperHelper.tryMoveSingleItem(to, takenItem, null);
                if (transferSuccess) {
                    to.setChanged();
                    from.setChanged();
                    cir.setReturnValue(true);
                    return;
                }
                //put the item back similar to vanilla
                ItemStack restoredStack = fromStackList.get(fromSlot);
                if (restoredStack.isEmpty()) {
                    restoredStack = takenItem;
                } else {
                    restoredStack.grow(1);
                }
                //calling setStack is necessary due to its side effects (markDirty in LootableContainerBlockEntity)
                from.setItem(fromSlot, restoredStack);
            }
        }
        hopperBlockEntity.myModCountAtLastExtract = hopperStackList.getModCount();
        if (fromStackList != null) {
            hopperBlockEntity.extractStackListModCount = fromStackList.getModCount();
        }
        cir.setReturnValue(false);
    }

    @Redirect(
            method = "tryMoveItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;Ljava/util/function/BooleanSupplier;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;inventoryFull()Z"
            )
    )
    private static boolean lithiumHopperIsFull(HopperBlockEntity hopperBlockEntity) {
        //noinspection ConstantConditions
        LithiumStackList lithiumStackList = InventoryHelper.getLithiumStackList((HopperBlockEntityMixin) (Object) hopperBlockEntity);
        return lithiumStackList.getFullSlots() == lithiumStackList.size();
    }

    @Redirect(
            method = "tryMoveItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;Ljava/util/function/BooleanSupplier;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;isEmpty()Z"
            )
    )
    private static boolean lithiumHopperIsEmpty(HopperBlockEntity hopperBlockEntity) {
        //noinspection ConstantConditions
        LithiumStackList lithiumStackList = InventoryHelper.getLithiumStackList((HopperBlockEntityMixin) (Object) hopperBlockEntity);
        return lithiumStackList.getOccupiedSlots() == 0;
    }

    @Shadow
    protected abstract void setCooldown(int cooldown);

    @Shadow
    protected abstract boolean isOnCooldown();

    @Shadow
    private static native boolean canTakeItemFromContainer(Container hopperInventory, Container fromInventory, ItemStack stack, int slot, Direction facing);

    @Shadow
    private Direction facing;

    @Shadow
    @Nullable
    private static native Container getBlockContainer(Level world, BlockPos pos, BlockState state);

    @Shadow
    @Nullable
    protected static native Container getSourceContainer(Level world, Hopper hopper, BlockPos pos, BlockState state);

    @Override
    public void lithium$invalidateCacheOnNeighborUpdate(boolean fromAbove) {
        //Clear the block inventory cache (composter inventories and no inventory present) on block update / observer update
        if (fromAbove) {
            if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.extractionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
                this.invalidateBlockExtractionData();
            }
        } else {
            if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.insertionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
                this.invalidateBlockInsertionData();
            }
        }
    }

    @Override
    public void lithium$invalidateCacheOnUndirectedNeighborUpdate() {
        if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.extractionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            this.invalidateBlockExtractionData();
        }
        if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.insertionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            this.invalidateBlockInsertionData();
        }
    }

    @Override
    public void lithium$invalidateCacheOnNeighborUpdate(Direction fromDirection) {
        boolean fromAbove = fromDirection == Direction.UP;
        if (fromAbove || this.getBlockState().getValue(HopperBlock.FACING) == fromDirection) {
            this.lithium$invalidateCacheOnNeighborUpdate(fromAbove);
        }
    }

    @Redirect(method = "ejectItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getAttachedContainer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Lnet/minecraft/world/Container;"))
    private static Container getLithiumOutputInventory(Level world, BlockPos pos, HopperBlockEntity blockEntity) {
        HopperBlockEntityMixin hopperBlockEntity = (HopperBlockEntityMixin) (Object) blockEntity;
        return hopperBlockEntity.getInsertInventory(world);
    }

    @Redirect(method = "suckInItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getItemsAtAndAbove(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Ljava/util/List;"))
    private static List<ItemEntity> lithiumGetInputItemEntities(Level world, Hopper hopper) {
        if (!(hopper instanceof HopperBlockEntityMixin hopperBlockEntity)) {
            return getItemsAtAndAbove(world, hopper); //optimizations not implemented for hopper minecarts
        }

        if (hopperBlockEntity.collectItemEntityTracker == null) {
            hopperBlockEntity.initCollectItemEntityTracker();
        }
        long modCount = InventoryHelper.getLithiumStackList(hopperBlockEntity).getModCount();
        if ((hopperBlockEntity.collectItemEntityTrackerWasEmpty || hopperBlockEntity.myModCountAtLastItemCollect == modCount) &&
                hopperBlockEntity.collectItemEntityTracker.isUnchangedSince(hopperBlockEntity.collectItemEntityAttemptTime)) {
            hopperBlockEntity.collectItemEntityAttemptTime = hopperBlockEntity.tickedGameTime;
            return Collections.emptyList();
        }
        hopperBlockEntity.myModCountAtLastItemCollect = modCount;
        hopperBlockEntity.shouldCheckSleep = false;

        List<ItemEntity> itemEntities = hopperBlockEntity.collectItemEntityTracker.getEntities(hopperBlockEntity.collectItemEntityBox);
        hopperBlockEntity.collectItemEntityAttemptTime = hopperBlockEntity.tickedGameTime;
        hopperBlockEntity.collectItemEntityTrackerWasEmpty = itemEntities.isEmpty();
        //set unchanged so that if this extract fails and there is no other change to hoppers or items, extracting
        // items can be skipped.
        return itemEntities;
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param insertInventory Block inventory / Blockentity inventory to be remembered
     */
    private void cacheInsertBlockInventory(Container insertInventory) {
        assert !(insertInventory instanceof Entity);
        if (insertInventory instanceof LithiumInventory optimizedInventory) {
            this.cacheInsertLithiumInventory(optimizedInventory);
        } else {
            this.insertInventory = null;
            this.insertStackList = null;
            this.insertStackListModCount = 0;
        }

        if (insertInventory instanceof BlockEntity || insertInventory instanceof CompoundContainer) {
            this.insertBlockInventory = insertInventory;
            if (insertInventory instanceof InventoryChangeTracker) {
                this.insertionMode = HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY;
                ((InventoryChangeTracker) insertInventory).listenForMajorInventoryChanges(this);
            } else {
                this.insertionMode = HopperCachingState.BlockInventory.BLOCK_ENTITY;
            }
        } else {
            if (insertInventory == null) {
                this.insertBlockInventory = null;
                this.insertionMode = HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY;
            } else {
                this.insertBlockInventory = insertInventory;
                this.insertionMode = insertInventory instanceof BlockStateOnlyInventory ? HopperCachingState.BlockInventory.BLOCK_STATE : HopperCachingState.BlockInventory.UNKNOWN;
            }
        }
    }

    private void cacheInsertLithiumInventory(LithiumInventory optimizedInventory) {
        LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
        this.insertInventory = optimizedInventory;
        this.insertStackList = insertInventoryStackList;
        this.insertStackListModCount = insertInventoryStackList.getModCount() - 1;
    }

    private void cacheExtractLithiumInventory(LithiumInventory optimizedInventory) {
        LithiumStackList extractInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
        this.extractInventory = optimizedInventory;
        this.extractStackList = extractInventoryStackList;
        this.extractStackListModCount = extractInventoryStackList.getModCount() - 1;
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param extractInventory Block inventory / Blockentity inventory to be remembered
     */
    private void cacheExtractBlockInventory(Container extractInventory) {
        assert !(extractInventory instanceof Entity);
        if (extractInventory instanceof LithiumInventory optimizedInventory) {
            this.cacheExtractLithiumInventory(optimizedInventory);
        } else {
            this.extractInventory = null;
            this.extractStackList = null;
            this.extractStackListModCount = 0;
        }

        if (extractInventory instanceof BlockEntity || extractInventory instanceof CompoundContainer) {
            this.extractBlockInventory = extractInventory;
            if (extractInventory instanceof InventoryChangeTracker) {
                this.extractionMode = HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY;
                ((InventoryChangeTracker) extractInventory).listenForMajorInventoryChanges(this);
            } else {
                this.extractionMode = HopperCachingState.BlockInventory.BLOCK_ENTITY;
            }
        } else {
            if (extractInventory == null) {
                this.extractBlockInventory = null;
                this.extractionMode = HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY;
            } else {
                this.extractBlockInventory = extractInventory;
                this.extractionMode = extractInventory instanceof BlockStateOnlyInventory ? HopperCachingState.BlockInventory.BLOCK_STATE : HopperCachingState.BlockInventory.UNKNOWN;
            }
        }
    }

    public Container getExtractBlockInventory(Level world, BlockPos extractBlockPos, BlockState extractBlockState) {
        Container blockInventory = this.extractBlockInventory;
        if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
            return null;
        } else if (this.extractionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            return blockInventory;
        } else if (this.extractionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            return blockInventory;
        } else if (this.extractionMode == HopperCachingState.BlockInventory.BLOCK_ENTITY) {
            BlockEntity blockEntity = (BlockEntity) Objects.requireNonNull(blockInventory);
            //Movable Block Entity compatibility - position comparison
            BlockPos pos = blockEntity.getBlockPos();
            if (!(blockEntity).isRemoved() && pos.equals(extractBlockPos)) {
                LithiumInventory optimizedInventory;
                if ((optimizedInventory = this.extractInventory) != null) {
                    LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
                    //This check is necessary as sometimes the stacklist is silently replaced (e.g. command making furnace read inventory from nbt)
                    if (insertInventoryStackList == this.extractStackList) {
                        return optimizedInventory;
                    } else {
                        this.invalidateBlockExtractionData();
                    }
                } else {
                    return blockInventory;
                }
            }
        }

        //No Cached Inventory: Get like vanilla and cache
        blockInventory = getBlockContainer(world, extractBlockPos, extractBlockState);
        blockInventory = HopperHelper.replaceDoubleInventory(blockInventory);
        this.cacheExtractBlockInventory(blockInventory);
        return blockInventory;
    }

    public Container getInsertBlockInventory(Level world) {
        Container blockInventory = this.insertBlockInventory;
        if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
            return null;
        } else if (this.insertionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            return blockInventory;
        } else if (this.insertionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            return blockInventory;
        } else if (this.insertionMode == HopperCachingState.BlockInventory.BLOCK_ENTITY) {
            BlockEntity blockEntity = (BlockEntity) Objects.requireNonNull(blockInventory);
            //Movable Block Entity compatibility - position comparison
            BlockPos pos = blockEntity.getBlockPos();
            Direction direction = this.facing;
            BlockPos transferPos = this.getBlockPos().relative(direction);
            if (!(blockEntity).isRemoved() &&
                    pos.equals(transferPos)) {
                LithiumInventory optimizedInventory;
                if ((optimizedInventory = this.insertInventory) != null) {
                    LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
                    //This check is necessary as sometimes the stacklist is silently replaced (e.g. command making furnace read inventory from nbt)
                    if (insertInventoryStackList == this.insertStackList) {
                        return optimizedInventory;
                    } else {
                        this.invalidateBlockInsertionData();
                    }
                } else {
                    return blockInventory;
                }
            }
        }

        //No Cached Inventory: Get like vanilla and cache
        Direction direction = this.facing;
        BlockPos insertBlockPos = this.getBlockPos().relative(direction);
        BlockState blockState = world.getBlockState(insertBlockPos);
        blockInventory = getBlockContainer(world, insertBlockPos, blockState);
        blockInventory = HopperHelper.replaceDoubleInventory(blockInventory);
        this.cacheInsertBlockInventory(blockInventory);
        return blockInventory;
    }


    public Container getInsertInventory(Level world) {
        Container blockInventory = this.getInsertBlockInventory(world);
        if (blockInventory != null) {
            return blockInventory;
        }

        if (this.insertInventoryEntityTracker == null) {
            this.initInsertInventoryTracker(world);
        }
        if (this.insertInventoryEntityTracker.isUnchangedSince(this.insertInventoryEntityFailedSearchTime)) {
            this.insertInventoryEntityFailedSearchTime = this.tickedGameTime;
            return null;
        }
        this.insertInventoryEntityFailedSearchTime = Long.MIN_VALUE;
        this.shouldCheckSleep = false;

        List<Container> inventoryEntities = this.insertInventoryEntityTracker.getEntities(this.insertInventoryEntityBox);
        if (inventoryEntities.isEmpty()) {
            this.insertInventoryEntityFailedSearchTime = this.tickedGameTime;
            //Remember failed entity search timestamp. This allows shortcutting if no entity movement happens.
            return null;
        }
        Container inventory = inventoryEntities.get(world.random.nextInt(inventoryEntities.size()));
        if (inventory instanceof LithiumInventory optimizedInventory) {
            LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
            if (inventory != this.insertInventory || this.insertStackList != insertInventoryStackList) {
                this.cacheInsertLithiumInventory(optimizedInventory);
            }
        }

        return inventory;
    }

    private void initCollectItemEntityTracker() {
        assert this.level instanceof ServerLevel;
        AABB inputBox = this.getSuckAabb().move(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        this.collectItemEntityBox = inputBox;
        this.collectItemEntityTracker =
                SectionedItemEntityMovementTracker.registerAt(
                        (ServerLevel) this.level,
                        inputBox,
                        ItemEntity.class
                );
        this.collectItemEntityAttemptTime = Long.MIN_VALUE;
    }

    private void initExtractInventoryTracker(Level world) {
        assert world instanceof ServerLevel;
        BlockPos pos = this.worldPosition.relative(Direction.UP);
        this.extractInventoryEntityBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        this.extractInventoryEntityTracker =
                SectionedInventoryEntityMovementTracker.registerAt(
                        (ServerLevel) this.level,
                        this.extractInventoryEntityBox,
                        Container.class
                );
        this.extractInventoryEntityFailedSearchTime = Long.MIN_VALUE;
    }

    private void initInsertInventoryTracker(Level world) {
        assert world instanceof ServerLevel;
        Direction direction = this.facing;
        BlockPos pos = this.worldPosition.relative(direction);
        this.insertInventoryEntityBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        this.insertInventoryEntityTracker =
                SectionedInventoryEntityMovementTracker.registerAt(
                        (ServerLevel) this.level,
                        this.insertInventoryEntityBox,
                        Container.class
                );
        this.insertInventoryEntityFailedSearchTime = Long.MIN_VALUE;
    }

    @Inject(method = "setBlockState(Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"))
    private void invalidateOnSetCachedState(BlockState state, CallbackInfo ci) {
        if (this.level != null && !this.level.isClientSide() && state.getValue(HopperBlock.FACING) != this.getBlockState().getValue(HopperBlock.FACING)) {
            this.invalidateCachedData();
        }
    }

    private void invalidateCachedData() {
        this.shouldCheckSleep = false;
        this.invalidateInsertionData();
        this.invalidateExtractionData();
    }

    private void invalidateInsertionData() {
        if (this.level instanceof ServerLevel serverWorld) {
            if (this.insertInventoryEntityTracker != null) {
                this.insertInventoryEntityTracker.unRegister(serverWorld);
                this.insertInventoryEntityTracker = null;
                this.insertInventoryEntityBox = null;
                this.insertInventoryEntityFailedSearchTime = 0L;
            }
        }

        this.invalidateBlockInsertionData();
    }

    private void invalidateBlockInsertionData() {
        if (this.insertionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            assert this.insertBlockInventory != null;
            ((InventoryChangeTracker) this.insertBlockInventory).stopListenForMajorInventoryChanges(this);
        }

        this.insertionMode = HopperCachingState.BlockInventory.UNKNOWN;
        this.insertBlockInventory = null;
        this.insertInventory = null;
        this.insertStackList = null;
        this.insertStackListModCount = 0;

        if (this instanceof SleepingBlockEntity sleepingBlockEntity) {
            sleepingBlockEntity.wakeUpNow();
        }
    }

    private void invalidateExtractionData() {
        if (this.level instanceof ServerLevel serverWorld) {
            if (this.extractInventoryEntityTracker != null) {
                this.extractInventoryEntityTracker.unRegister(serverWorld);
                this.extractInventoryEntityTracker = null;
                this.extractInventoryEntityBox = null;
                this.extractInventoryEntityFailedSearchTime = 0L;
            }
            if (this.collectItemEntityTracker != null) {
                this.collectItemEntityTracker.unRegister(serverWorld);
                this.collectItemEntityTracker = null;
                this.collectItemEntityBox = null;
                this.collectItemEntityTrackerWasEmpty = false;
            }
        }
        this.invalidateBlockExtractionData();
    }

    private void invalidateBlockExtractionData() {
        if (this.extractionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            assert this.extractBlockInventory != null;
            ((InventoryChangeTracker) this.extractBlockInventory).stopListenForMajorInventoryChanges(this);
        }

        this.extractionMode = HopperCachingState.BlockInventory.UNKNOWN;
        this.extractBlockInventory = null;
        this.extractInventory = null;
        this.extractStackList = null;
        this.extractStackListModCount = 0;

        if (this instanceof SleepingBlockEntity sleepingBlockEntity) {
            sleepingBlockEntity.wakeUpNow();
        }
    }

    @Inject(
            method = "pushItemsTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;tryMoveItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;Ljava/util/function/BooleanSupplier;)Z",
                    shift = At.Shift.AFTER
            )
    )
    private static void checkSleepingConditions(Level world, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, CallbackInfo ci) {
        ((HopperBlockEntityMixin) (Object) blockEntity).checkSleepingConditions();
    }

    private void checkSleepingConditions() {
        if (this.isOnCooldown() || this.getLevel() == null) {
            return;
        }
        if (this instanceof SleepingBlockEntity thisSleepingBlockEntity) {
            if (thisSleepingBlockEntity.isSleeping()) {
                return;
            }
            if (!this.shouldCheckSleep) {
                this.shouldCheckSleep = true;
                return;
            }
            if (this instanceof InventoryChangeTracker thisTracker) {
                boolean listenToExtractTracker = false;
                boolean listenToInsertTracker = false;
                boolean listenToExtractEntities = false;
                boolean listenToItemEntities = false;
                boolean listenToInsertEntities = false;

                LithiumStackList thisStackList = InventoryHelper.getLithiumStackList(this);

                if (this.extractionMode != HopperCachingState.BlockInventory.BLOCK_STATE && thisStackList.getFullSlots() != thisStackList.size()) {
                    if (this.extractionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
                        Container blockInventory = this.extractBlockInventory;
                        if (this.extractStackList != null &&
                                blockInventory instanceof InventoryChangeTracker) {
                            if (this.extractStackList.maybeSendsComparatorUpdatesOnFailedExtract() && this.extractStackList.getOccupiedSlots() != 0) {
                                if (blockInventory instanceof ComparatorTracker comparatorTracker && !comparatorTracker.lithium$hasAnyComparatorNearby()) {
                                    listenToExtractTracker = true;
                                } else {
                                    //Inventory is not empty (0 != number of occupied slots) and maybe sends comparator
                                    // updates on failed extract attempts, so hopper must not sleep to be able to send
                                    // the observable comparator updates.
                                    return;
                                }
                            } else {
                                listenToExtractTracker = true;
                            }
                        } else {
                            return;
                        }
                    } else if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
                        BlockState hopperState = this.getBlockState();
                        if (PlatformModCompat.INSTANCE.canHopperInteractWithApiBlockInventory((HopperBlockEntity) (Object) this, hopperState, true)) {
                            return;
                        }
                        listenToExtractEntities = true;

                        BlockPos blockPos = this.getBlockPos().above();
                        BlockState blockState = this.getLevel().getBlockState(blockPos);
                        if (!blockState.isCollisionShapeFullBlock(this.getLevel(), blockPos) || blockState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS)) {
                            listenToItemEntities = true;
                        }
                    } else {
                        return;
                    }
                }
                if (this.insertionMode != HopperCachingState.BlockInventory.BLOCK_STATE && 0 < thisStackList.getOccupiedSlots()) {
                    if (this.insertionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
                        Container blockInventory = this.insertBlockInventory;
                        if (this.insertStackList != null && blockInventory instanceof InventoryChangeTracker) {
                            listenToInsertTracker = true;
                        } else {
                            return;
                        }
                    } else if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
                        BlockState hopperState = this.getBlockState();
                        if (PlatformModCompat.INSTANCE.canHopperInteractWithApiBlockInventory((HopperBlockEntity) (Object) this, hopperState, false)) {
                            return;
                        }
                        listenToInsertEntities = true;
                    } else {
                        return;
                    }
                }

                if (listenToExtractTracker) {
                    ((InventoryChangeTracker) this.extractBlockInventory).listenForContentChangesOnce(this.extractStackList, this);
                }
                if (listenToInsertTracker) {
                    ((InventoryChangeTracker) this.insertBlockInventory).listenForContentChangesOnce(this.insertStackList, this);
                }
                if (listenToInsertEntities) {
                    if (this.insertInventoryEntityTracker == null) {
                        return;
                    }
                    this.insertInventoryEntityTracker.listenToEntityMovementOnce(this);
                }
                if (listenToExtractEntities) {
                    if (this.extractInventoryEntityTracker == null) {
                        return;
                    }
                    this.extractInventoryEntityTracker.listenToEntityMovementOnce(this);
                }
                if (listenToItemEntities) {
                    if (this.collectItemEntityTracker != null) {
                        this.collectItemEntityTracker.listenToEntityMovementOnce(this);
                    } else {
                        return;
                    }
                }

                thisTracker.listenForContentChangesOnce(thisStackList, this);
                thisSleepingBlockEntity.lithium$startSleeping();
            }
        }
    }

    @Override
    public void lithium$handleInventoryContentModified(Container inventory) {
        if (this instanceof SleepingBlockEntity sleepingBlockEntity) {
            sleepingBlockEntity.wakeUpNow();
        }
    }

    @Override
    public void lithium$handleInventoryRemoved(Container inventory) {
        if (this instanceof SleepingBlockEntity sleepingBlockEntity) {
            sleepingBlockEntity.wakeUpNow();
        }
        if (inventory == this.insertBlockInventory) {
            this.invalidateBlockInsertionData();
        }
        if (inventory == this.extractBlockInventory) {
            this.invalidateBlockExtractionData();
        }
        if (inventory == this) {
            this.invalidateCachedData();
        }
    }

    @Override
    public boolean lithium$handleComparatorAdded(Container inventory) {
        if (inventory == this.extractBlockInventory && this instanceof SleepingBlockEntity sleepingBlockEntity) {
            sleepingBlockEntity.wakeUpNow();
            return true;
        }
        return false;
    }

    @Override
    public void lithium$handleEntityMovement(Class<?> category) {
        if (this instanceof SleepingBlockEntity sleepingBlockEntity) {
            sleepingBlockEntity.wakeUpNow();
        }
    }
}
