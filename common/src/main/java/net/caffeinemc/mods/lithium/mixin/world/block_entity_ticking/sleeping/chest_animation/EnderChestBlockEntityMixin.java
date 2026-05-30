package net.caffeinemc.mods.lithium.mixin.world.block_entity_ticking.sleeping.chest_animation;

import net.caffeinemc.mods.lithium.common.block.entity.SleepingBlockEntity;
import net.caffeinemc.mods.lithium.mixin.world.block_entity_ticking.sleeping.WrappedBlockEntityTickInvokerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderChestBlockEntity.class)
public abstract class EnderChestBlockEntityMixin implements SleepingBlockEntity {

    @Shadow
    public abstract float getOpenNess(float f);

    @Unique
    private WrappedBlockEntityTickInvokerAccessor tickWrapper = null;
    @Unique
    private TickingBlockEntity sleepingTicker = null;

    @Override
    public WrappedBlockEntityTickInvokerAccessor lithium$getTickWrapper() {
        return this.tickWrapper;
    }

    @Override
    public void lithium$setTickWrapper(WrappedBlockEntityTickInvokerAccessor tickWrapper) {
        this.tickWrapper = tickWrapper;
        this.lithium$setSleepingTicker(null);
    }

    @Override
    public TickingBlockEntity lithium$getSleepingTicker() {
        return this.sleepingTicker;
    }

    @Override
    public void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    @Inject(
            method = "triggerEvent(II)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/ChestLidController;shouldBeOpen(Z)V")
    )
    private void wakeUpOnSyncedBlockEvent(int type, int data, CallbackInfoReturnable<Boolean> cir) {
        if (this.sleepingTicker != null) {
            this.wakeUpNow();
        }
    }

    @Inject(
            method = "lidAnimateTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/EnderChestBlockEntity;)V",
            at = @At(value = "RETURN")
    )
    private static void sleepOnAnimationEnd(Level level, BlockPos blockPos, BlockState blockState, EnderChestBlockEntity enderChestBlockEntity, CallbackInfo ci) {
        ((EnderChestBlockEntityMixin) (Object) enderChestBlockEntity).checkSleep();
    }

    @Unique
    private void checkSleep() {
        //If the animation is finished, it will stay unchanged until the next triggerEvent, which may change shouldBeOpen
        if (this.getOpenNess(0.0F) == this.getOpenNess(1.0F)) {
            this.lithium$startSleeping();
        }
    }
}