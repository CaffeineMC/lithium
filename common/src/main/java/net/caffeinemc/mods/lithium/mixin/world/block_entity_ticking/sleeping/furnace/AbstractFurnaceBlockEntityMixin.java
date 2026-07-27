package net.caffeinemc.mods.lithium.mixin.world.block_entity_ticking.sleeping.furnace;

import net.caffeinemc.mods.lithium.common.block.entity.SetChangedHandlingBlockEntity;
import net.caffeinemc.mods.lithium.common.block.entity.SleepingBlockEntity;
import net.caffeinemc.mods.lithium.mixin.world.block_entity_ticking.sleeping.WrappedBlockEntityTickInvokerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin extends BlockEntity implements SleepingBlockEntity, SetChangedHandlingBlockEntity {

    @SuppressWarnings("ShadowModifiers")
    @Shadow
    public int cookingTimer;
    @SuppressWarnings("ShadowModifiers")
    @Shadow
    public int litTimeRemaining;
    @Unique
    private WrappedBlockEntityTickInvokerAccessor tickWrapper = null;
    @Unique
    private TickingBlockEntity sleepingTicker = null;

    public AbstractFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public WrappedBlockEntityTickInvokerAccessor lithium$getTickWrapper() {
        return tickWrapper;
    }

    @Override
    public void lithium$setTickWrapper(WrappedBlockEntityTickInvokerAccessor tickWrapper) {
        this.tickWrapper = tickWrapper;
        this.lithium$setSleepingTicker(null);
    }

    @Override
    public TickingBlockEntity lithium$getSleepingTicker() {
        return sleepingTicker;
    }

    @Override
    public void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void checkSleep(ServerLevel serverLevel, BlockPos blockPos, BlockState blockState, AbstractFurnaceBlockEntity abstractFurnaceBlockEntity, CallbackInfo ci) {
        ((AbstractFurnaceBlockEntityMixin) (Object) abstractFurnaceBlockEntity).checkSleep(blockState);
    }

    @Unique
    private boolean isLit() {
        return this.litTimeRemaining > 0;
    }

    @Unique
    private void checkSleep(BlockState state) {
        if (!this.isLit() && this.cookingTimer == 0 && (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) && this.level != null) {
            this.lithium$startSleeping();
        }
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void wakeUpAfterFromTag(CallbackInfo ci) {
        if (this.isSleeping() && this.level != null && !this.level.isClientSide()) {
            this.wakeUpNow();
        }
    }

    //Temporary workaround for vanilla bug where furnaces are not marked for saving when their inventory is modified at slot != 0.
    // This doesn't fix the vanilla bug, but the furnace will wake up correctly.
    // https://github.com/CaffeineMC/lithium/issues/765
    @Inject(method = "setItem", at = @At("RETURN"))
    private void handleSetChanged(CallbackInfo ci) {
        this.lithium$handleSetChanged();
    }

    @Override
    public void lithium$handleSetChanged() {
        if (this.isSleeping() && this.level != null && !this.level.isClientSide()) {
            this.wakeUpNow();
        }
    }
}
