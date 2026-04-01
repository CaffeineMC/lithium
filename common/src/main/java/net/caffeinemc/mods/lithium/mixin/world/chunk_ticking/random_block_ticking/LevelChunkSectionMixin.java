package net.caffeinemc.mods.lithium.mixin.world.chunk_ticking.random_block_ticking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData;
import net.caffeinemc.mods.lithium.common.world.section.RandomTickingSectionDataHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = LevelChunkSection.class, priority = 2000 /*Apply after other mixins to pass lithium block counter object as outermost*/)
public abstract class LevelChunkSectionMixin implements LithiumSectionData {

    @Shadow
    @Final
    private PalettedContainer<BlockState> states;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/chunk/PalettedContainerFactory;)V",
            at = @At("RETURN")
    )
    private void initAirSection(PalettedContainerFactory palettedContainerFactory, CallbackInfo ci) {
        SectionData sectionData = this.lithium$getSectionData();
        //Instead of initializing all flag counters to 0, initialize them correctly in case they accept air. The entire section should always be air here.

        if (sectionData.getRandomTickableBlocksByY() != null) {
            throw new IllegalStateException("RandomTickableBlocksByY already initialized!");
        }
        sectionData.setRandomTickableBlocksByY(new byte[RandomTickingSectionDataHelper.BYTE_COUNT]);
        //In case air is random tickable, initialize the counts to all set
        if (this.states.maybeHas(BlockStateFlags.RANDOM_TICKING)) {
            RandomTickingSectionDataHelper.initDataForAllRandomTickingSection(sectionData);
        } else {
            RandomTickingSectionDataHelper.initNonRandomTickingSection(sectionData);
        }
    }

    @Inject(method = "recalcBlockCounts()V", at = @At("HEAD"))
    private void createFlagCounters(CallbackInfo ci) {
        this.lithium$getSectionData().setRandomTickableBlocksByY(new byte[RandomTickingSectionDataHelper.BYTE_COUNT]);
    }

    @WrapOperation(
            method = "recalcBlockCounts()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/PalettedContainer;count(Lnet/minecraft/world/level/chunk/PalettedContainer$CountConsumer;)V"
            )
    )
    private void initFlagCounters(PalettedContainer<BlockState> instance, PalettedContainer.CountConsumer<BlockState> countConsumer, Operation<Void> original) {
        byte[] randomTickableBlocksByY = Objects.requireNonNull(this.lithium$getSectionData().getRandomTickableBlocksByY());
        RandomTickingSectionDataHelper.LithiumBlockCounter lithiumBlockCounter = new RandomTickingSectionDataHelper.LithiumBlockCounter(randomTickableBlocksByY, countConsumer);
        original.call(instance, lithiumBlockCounter);
        lithiumBlockCounter.handleAfterCounting((LevelChunkSection) (Object) this);
    }


    @Inject(
            method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void updateRandomTickableBlockCounts(int x, int y, int z, BlockState newState, boolean lock, CallbackInfoReturnable<BlockState> cir, @Local(name = "previous") BlockState oldState) {
        int prevFlags = ((BlockStateFlagHolder) oldState).lithium$getAllFlags();
        int flags = ((BlockStateFlagHolder) newState).lithium$getAllFlags();

        int mask = 1 << BlockStateFlags.RANDOM_TICKING.getIndex();

        if ((prevFlags & mask) != (flags & mask)) {
            if ((prevFlags & mask) != 0) {
                RandomTickingSectionDataHelper.removeAt(x, y, z, this.lithium$getSectionDataDirect().getRandomTickableBlocksByY());
            } else {
                RandomTickingSectionDataHelper.addAt(x, y, z, this.lithium$getSectionDataDirect().getRandomTickableBlocksByY());
            }
        }
    }
}
