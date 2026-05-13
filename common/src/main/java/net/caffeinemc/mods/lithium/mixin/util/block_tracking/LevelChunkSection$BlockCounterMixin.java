package net.caffeinemc.mods.lithium.mixin.util.block_tracking;

import net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder;
import net.caffeinemc.mods.lithium.common.tracking.block.LithiumBlockCounter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Add initialization of lithium's block counters to the vanilla block counter
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunkSection$1BlockCounter")
public abstract class LevelChunkSection$BlockCounterMixin implements LithiumBlockCounter {

    @Unique
    short[] countsByFlag;

    @Override
    public void lithium$initBlockCounter(short[] countsByFlag) {
        this.countsByFlag = countsByFlag;
    }

    @Inject(
            method = "accept(Lnet/minecraft/world/level/block/state/BlockState;I)V", at = @At("HEAD")
    )
    public void acceptLithium(BlockState state, int count, CallbackInfo ci) {
        addToFlagCount(this.countsByFlag, state, (short) count);
    }

    @Unique
    private static void addToFlagCount(short[] countsByFlag, BlockState state, short change) {
        int flags = ((BlockStateFlagHolder) state).lithium$getAllFlags();
        int i;
        while ((i = Integer.numberOfTrailingZeros(flags)) < 32 && i < countsByFlag.length) {
            //either count up by one (prevFlag not set) or down by one (prevFlag set)
            countsByFlag[i] += change;
            flags &= ~(1 << i);
        }
    }
}
