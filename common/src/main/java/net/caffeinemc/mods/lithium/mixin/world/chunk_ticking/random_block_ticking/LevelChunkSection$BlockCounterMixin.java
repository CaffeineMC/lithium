package net.caffeinemc.mods.lithium.mixin.world.chunk_ticking.random_block_ticking;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.caffeinemc.mods.lithium.common.block.BlockCountingSection;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.world.section.LithiumSectionData;
import net.caffeinemc.mods.lithium.common.world.section.RandomTickingSectionDataHelper;
import net.caffeinemc.mods.lithium.mixin.LithiumMixinPlugin;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Add initialization of the minisection counts to the vanilla block counter
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunkSection$1BlockCounter")
public class LevelChunkSection$BlockCounterMixin implements RandomTickingSectionDataHelper.LithiumRandomTickingBlockCounter {

    @Unique
    private byte[] randomTickData;
    @Unique
    private byte lastRandomTickableBlockCountTotal;
    @Unique
    private int minisectionIndex;


    @Override
    public void lithium$init(byte[] randomTickData) {
        this.randomTickData = randomTickData;
        this.lastRandomTickableBlockCountTotal = 0;
        this.minisectionIndex = 0;
    }

    @Override
    public void lithium$finishedCountingMinisection(Int2IntOpenHashMap indexCounts, Palette<BlockState> palette) {
        //A bunch of bytes can over- and underflow here, but actually it is no issue
        //Subtract the previous total first, since the new total is added in the forEach below
        this.randomTickData[this.minisectionIndex] -= this.lastRandomTickableBlockCountTotal;
        indexCounts.int2IntEntrySet().forEach(entry -> {
            BlockState blockState = palette.valueFor(entry.getIntKey());
            if ((((BlockStateFlagHolder) blockState).lithium$getAllFlags() & RandomTickingSectionDataHelper.RANDOM_TICKING_FLAG_MASK) != 0) {
                this.randomTickData[this.minisectionIndex] += (byte) entry.getIntValue();
            }
        });
        this.lastRandomTickableBlockCountTotal += this.randomTickData[this.minisectionIndex];

        this.minisectionIndex++;
    }


    @Override
    public <T> void lithium$wholeSectionSingleBlock(T singleBlockState, int count) {
        if (count != 4096) {
            return; //handleAfterCounting will fall back to scanning the sections blocks
        }

        if (singleBlockState instanceof BlockState state) {
            RandomTickingSectionDataHelper.handleSectionSingleBlockState(state, this.randomTickData);
            this.minisectionIndex = RandomTickingSectionDataHelper.MINISECTION_COUNT;
        }

    }

    @Override
    public void lithium$handleAfterCounting(LevelChunkSection section) {
        if (RandomTickingSectionDataHelper.MINISECTION_COUNT != this.minisectionIndex) { //Mod compatibility issue fallback - Mixin in PalettedContainer could not detect our counter, as another mod wrapped it again or another method of counting blocks was used instead.
            if (this.randomTickData != ((LithiumSectionData) section).lithium$getSectionData().getRandomTickableBlocksByY()) {
                throw new IllegalArgumentException("Lithium random tick data was replaced unexpectedly!");
            }
            RandomTickingSectionDataHelper.naiveInitializeData(section.getStates(), this.randomTickData);
        }

        if (LithiumMixinPlugin.DEBUG) {
            this.sanityCheckRandomTickableBlockCount((BlockCountingSection) section);
        }
    }

    @Unique
    private void sanityCheckRandomTickableBlockCount(BlockCountingSection section) {
        // Sanity check: Total random block count equals sum of minisection counts:

        int randomTickableStatesCount = section.lithium$getCount(BlockStateFlags.RANDOM_TICKING);
        int sum = 0;
        byte[] tickData = this.randomTickData;
        for (int i = 0; i < tickData.length; i++) {
            byte randomTickDatum = tickData[i];
            sum += Byte.toUnsignedInt(randomTickDatum);
            if (i == tickData.length - 1) {
                if (Byte.toUnsignedInt(randomTickDatum) > RandomTickingSectionDataHelper.LAST_MINISECTION_SIZE) {
                    throw new IllegalStateException("Lithium random tick data contains too large last minisection value: " + randomTickDatum + " > " + RandomTickingSectionDataHelper.LAST_MINISECTION_SIZE);
                }
            }
        }
        if (randomTickableStatesCount != sum) {
            throw new IllegalStateException("Lithium random tick data initialization calculated inconsistent results: " + randomTickableStatesCount + " != " + sum);
        }
    }

}
