package net.caffeinemc.mods.lithium.mixin.util.block_tracking;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.block.*;
import net.caffeinemc.mods.lithium.common.tracking.block.ChunkSectionChangeCallback;
import net.caffeinemc.mods.lithium.common.tracking.block.LithiumBlockCounter;
import net.caffeinemc.mods.lithium.common.tracking.block.SectionedBlockChangeTracker;
import net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Keep track of how many blocks that meet certain criteria are in this chunk section.
 * E.g. if no over-sized blocks are there, collision code can skip a few blocks.
 *
 * @author 2No2Name
 */
@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements BlockCountingSection, BlockListeningSection, LithiumSectionData {

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

        if (sectionData.getCountsByFlag() != null) {
            throw new IllegalStateException("CountsByFlag already initialized!");
        }
        sectionData.setCountsByFlag(new short[BlockStateFlags.NUM_TRACKED_FLAGS]);
        for (TrackedBlockStatePredicate trackedBlockStatePredicate : BlockStateFlags.TRACKED_FLAGS) {
            if (this.states.maybeHas(trackedBlockStatePredicate)) {
                sectionData.getCountsByFlag()[trackedBlockStatePredicate.getIndex()] = 16 * 16 * 16;
            }
        }
    }

    @ModifyArg(
            method = "recalcBlockCounts()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/PalettedContainer;count(Lnet/minecraft/world/level/chunk/PalettedContainer$CountConsumer;)V"
            )
    )
    private PalettedContainer.CountConsumer<BlockState> initLithiumBlockCounter(PalettedContainer.CountConsumer<BlockState> countConsumer) {

        if (countConsumer instanceof LithiumBlockCounter lithiumBlockCounter) {
            short[] countsByFlag = Objects.requireNonNull(this.lithium$getSectionData().getCountsByFlag());
            lithiumBlockCounter.lithium$init(countsByFlag);
        }

        return countConsumer;
    }

    @Inject(method = "recalcBlockCounts()V", at = @At("HEAD"))
    private void createFlagCounters(CallbackInfo ci) {
        this.lithium$getSectionData().setCountsByFlag(new short[BlockStateFlags.NUM_TRACKED_FLAGS]);
    }

    @Inject(
            method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(value = "HEAD")
    )
    private void resetData(FriendlyByteBuf buf, CallbackInfo ci) {
        this.lithium$getSectionData().setCountsByFlag(null);
    }

    @Inject(
            method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(value = "RETURN")
    )
    private void updateFlagCounters(int x, int y, int z, BlockState newState, boolean lock, CallbackInfoReturnable<BlockState> cir, @Local(name = "previous") BlockState oldState) {
        this.lithium$trackBlockStateChange(newState, oldState);
        ChunkSectionChangeCallback changeListener = this.lithium$getSectionData().getChangeListener();
        if (changeListener != null) {
            changeListener.onBlockChange(this, x, y, z, oldState, newState);
        }
    }

    @Override
    public short lithium$getCount(int predicateIndex) {
        SectionData sectionData = this.lithium$getSectionData();
        if (sectionData.getCountsByFlag() == null) {
            fastInitClientCounts();
        }
        return sectionData.getCountsByFlag()[predicateIndex];
    }

    @Override
    public boolean lithium$mayContainAny(TrackedBlockStatePredicate trackedBlockStatePredicate) {
        SectionData sectionData = this.lithium$getSectionData();
        if (sectionData.getCountsByFlag() == null) {
            this.fastInitClientCounts();
        }
        return sectionData.getCountsByFlag()[trackedBlockStatePredicate.getIndex()] != (short) 0;
    }

    @Unique
    private void fastInitClientCounts() {
        SectionData sectionData = this.lithium$getSectionData();
        sectionData.setCountsByFlag(new short[BlockStateFlags.NUM_TRACKED_FLAGS]);
        for (TrackedBlockStatePredicate trackedBlockStatePredicate : BlockStateFlags.TRACKED_FLAGS) {
            if (this.states.maybeHas(trackedBlockStatePredicate)) {
                //We haven't counted, so we just set the count so high that it never incorrectly reaches 0.
                //For most situations, this overestimation does not hurt client performance compared to correct counting,
                sectionData.getCountsByFlag()[trackedBlockStatePredicate.getIndex()] = 16 * 16 * 16;
            }
        }
    }

    @Override
    public void lithium$trackBlockStateChange(BlockState newState, BlockState oldState) {
        short[] countsByFlag = this.lithium$getSectionData().getCountsByFlag();
        if (countsByFlag == null) {
            return;
        }
        int prevFlags = ((BlockStateFlagHolder) oldState).lithium$getAllFlags();
        int flags = ((BlockStateFlagHolder) newState).lithium$getAllFlags();

        int flagsXOR = prevFlags ^ flags;
        //iterate over indices that changed and update each counter
        int flagIndex;

        while ((flagIndex = Integer.numberOfTrailingZeros(flagsXOR)) < 32 && flagIndex < countsByFlag.length) {
            int flagBit = 1 << flagIndex;
            //either count up by one (prevFlag not set) or down by one (prevFlag set)
            if ((flagsXOR & flagBit) != 0) {
                countsByFlag[flagIndex] += (short) (1 - (((prevFlags >>> flagIndex) & 1) << 1));
            }
            flagsXOR &= ~flagBit;
        }
    }

    @Override
    public void lithium$addToCallback(SectionedBlockChangeTracker tracker, long sectionPos, Level world) {
        SectionData sectionData = this.lithium$getSectionData();
        if (sectionData.getChangeListener() == null) {
            if (sectionPos == Long.MIN_VALUE || world == null) {
                throw new IllegalArgumentException("Expected world and section pos during intialization!");
            }
            sectionData.setChangeListener(ChunkSectionChangeCallback.create(sectionPos, world));
        }

        sectionData.getChangeListener().addTracker(tracker);
    }

    @Override
    public void lithium$removeFromCallback(SectionedBlockChangeTracker tracker) {
        ChunkSectionChangeCallback changeListener = this.lithium$getSectionData().getChangeListener();
        if (changeListener != null) {
            changeListener.removeTracker(tracker);
        }
    }
}
