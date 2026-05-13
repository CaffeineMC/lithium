package net.caffeinemc.mods.lithium.mixin.world.chunk_ticking.random_block_ticking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.caffeinemc.mods.lithium.common.world.section.RandomTickingSectionDataHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.IntConsumer;

@Mixin(PalettedContainer.class)
public class PalettedContainerMixin<T> {

    @Shadow
    private volatile PalettedContainer.Data<T> data;

    @ModifyArg(method = "count", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BitStorage;getAll(Ljava/util/function/IntConsumer;)V"))
    private IntConsumer initializeRandomTickExtraData(IntConsumer originalConsumer, @Local(argsOnly = true) PalettedContainer.CountConsumer<T> countConsumer, @Local(name = "counts") Int2IntOpenHashMap indexCounts) {
        if (countConsumer instanceof RandomTickingSectionDataHelper.LithiumRandomTickingBlockCounter lithiumRandomTickingBlockCounter) {
            Palette<T> palette = this.data.palette();
            return new IntConsumer() {
                int index = 0;

                @Override
                public void accept(int value) {
                    originalConsumer.accept(value);

                    this.index++;
                    if (this.index % RandomTickingSectionDataHelper.MINISECTION_SIZE == 0 || this.index == 4096) {
                        //noinspection unchecked
                        lithiumRandomTickingBlockCounter.lithium$finishedCountingMinisection(indexCounts, (Palette<BlockState>) palette);
                    }
                }
            };

        }
        return originalConsumer;
    }

    @WrapOperation(method = "count", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer$CountConsumer;accept(Ljava/lang/Object;I)V"))
    private void initializeRandomTickExtraData(PalettedContainer.CountConsumer<T> countConsumer, T singleBlockState, int count, Operation<Void> original) {
        if (countConsumer instanceof RandomTickingSectionDataHelper.LithiumRandomTickingBlockCounter lithiumRandomTickingBlockCounter) {
            lithiumRandomTickingBlockCounter.lithium$wholeSectionSingleBlock(singleBlockState, count);
        }
        original.call(countConsumer, singleBlockState, count);
    }
}
