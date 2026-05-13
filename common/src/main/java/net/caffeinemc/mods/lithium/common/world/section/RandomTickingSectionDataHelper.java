package net.caffeinemc.mods.lithium.common.world.section;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;

import java.util.Arrays;

public class RandomTickingSectionDataHelper {

    //The range 0..256 does not fit into 8 bit, that is why this only goes to 248.
    public static final int MINISECTION_SIZE = 248;

    public static final int MINISECTION_BITS = Mth.ceillog2(MINISECTION_SIZE);
    public static final int MINISECTION_COUNT = Mth.ceil(4096 / (float) MINISECTION_SIZE);
    public static final int LAST_MINISECTION_SIZE = 4096 - (MINISECTION_SIZE * (MINISECTION_COUNT - 1));

    public static final int MINISECTIONS_PER_BYTE = 8 / MINISECTION_BITS;
    public static final int BYTE_COUNT = Mth.ceil(MINISECTION_COUNT / (float) MINISECTIONS_PER_BYTE);

    public static final int RANDOM_TICKING_FLAG_MASK = 1 << BlockStateFlags.RANDOM_TICKING.getIndex();

    public static void naiveInitializeData(PalettedContainer<BlockState> states, byte[] data) {
        if (data.length != BYTE_COUNT) {
            throw new IllegalArgumentException("Invalid data length: " + data.length + ", expected " + BYTE_COUNT);
        }
        Arrays.fill(data, 0, BYTE_COUNT, (byte) 0);

        if (!states.maybeHas(blockState -> blockState.isRandomlyTicking() || blockState.getFluidState().isRandomlyTicking())) {
            return;
        }
        //In case the section has random tickable blocks, naive linear scan for those blocks
        for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
            BlockState blockState = states.get(unpackX(blockIndex), unpackY(blockIndex), unpackZ(blockIndex));
            if ((((BlockStateFlagHolder) blockState).lithium$getAllFlags() & RANDOM_TICKING_FLAG_MASK) != 0) {
                int byteIndex = blockIndex / MINISECTION_SIZE;
                data[byteIndex]++;
            }
        }
    }

    public static void handleSectionSingleBlockState(BlockState state, byte[] randomTickData) {
        if (state.isRandomlyTicking() || state.getFluidState().isRandomlyTicking()) {
            initDataForAllRandomTickingSection(randomTickData);
        } else {
            initNonRandomTickingSection(randomTickData);
        }
    }

    public interface LithiumRandomTickingBlockCounter {

        void lithium$initRandomTickingBlockCounter(byte[] randomTickableBlocksByY);

        void lithium$finishedCountingMinisection(Int2IntOpenHashMap indexCounts, short[] indexCountsArray /*For compatibility with serialization optimization*/, Palette<BlockState> palette);

        <T> void lithium$wholeSectionSingleBlock(T singleBlockState, int count);

        void lithium$handleAfterCounting(LevelChunkSection section);
    }

    public static void initDataForAllRandomTickingSection(LithiumSectionData.SectionData sectionData) {
        initDataForAllRandomTickingSection(sectionData.getRandomTickableBlocksByY());
    }

    public static void initDataForAllRandomTickingSection(byte[] randomTickableBlocksByY) {
        Arrays.fill(randomTickableBlocksByY, (byte) RandomTickingSectionDataHelper.MINISECTION_SIZE);
        randomTickableBlocksByY[randomTickableBlocksByY.length - 1] = (byte) RandomTickingSectionDataHelper.LAST_MINISECTION_SIZE;
    }

    public static void initNonRandomTickingSection(LithiumSectionData.SectionData sectionData) {
        initNonRandomTickingSection(sectionData.getRandomTickableBlocksByY());
    }

    public static void initNonRandomTickingSection(byte[] randomTickableBlocksByY) {
        Arrays.fill(randomTickableBlocksByY, (byte) 0);
    }


    public static void randomTickNthBlock(LevelChunkSection section, int randomBlockIndex, byte[] data, ServerLevel level, int sectionBlockX, int sectionBlockY, int sectionBlockZ, RandomSource random) {
        assert data.length == BYTE_COUNT;
        //Section block coordinates must be the lower corner of the chunk section
        assert (sectionBlockX & 0xF) == 0;
        assert (sectionBlockY & 0xF) == 0;
        assert (sectionBlockZ & 0xF) == 0;

        //Find the corresponding minisection that the block is in
        int minisectionIndex = 0;
        for (; minisectionIndex < data.length; minisectionIndex++) {
            int countInMinisection = Byte.toUnsignedInt(data[minisectionIndex]);
            if (randomBlockIndex >= countInMinisection) {
                randomBlockIndex -= countInMinisection;
            } else {
                break;
            }
        }
        //Search from the minisection start until hitting the block, which should be in that minisection
        for (int blockIndex = minisectionIndex * MINISECTION_SIZE; blockIndex < 4096; blockIndex++) {

            //Convert [0..4095] to x,y,z in [0..15], consistently with the minisections and in the order vanilla stores the blocks in the bit storage
            int x = unpackX(blockIndex);
            int y = unpackY(blockIndex);
            int z = unpackZ(blockIndex);

            BlockState blockState = section.getBlockState(x, y, z);
            if ((((BlockStateFlagHolder) blockState).lithium$getAllFlags() & RANDOM_TICKING_FLAG_MASK) != 0) {
                if (randomBlockIndex-- == 0) {
                    //Vanilla always ticks the block first followed by the fluid with an immutable block position. Do not use Mutable block pos here.
                    BlockPos immutableRandomTickPos = new BlockPos(sectionBlockX | x, sectionBlockY | y, sectionBlockZ | z);
                    if (blockState.isRandomlyTicking()) {
                        blockState.randomTick(level, immutableRandomTickPos, random);
                    }
                    FluidState fluidState = blockState.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick(level, immutableRandomTickPos, random);
                    }
                    return;
                }
            }
        }

        throw new IllegalStateException("Failed to find random tickable position! This means lithium's random tickable block optimization encountered inconsistent data, hinting at a mod compatibility issue.");
    }

    public static void addAt(int x, int y, int z, byte[] data) {
        data[getMinisectionIndex(x, y, z)]++;
    }

    public static void removeAt(int x, int y, int z, byte[] data) {
        data[getMinisectionIndex(x, y, z)]--;
    }

    public static int getMinisectionIndex(int x, int y, int z) {
        return pack(x, y, z) / MINISECTION_SIZE;
    }

    private static int pack(int x, int y, int z) {
        return (y << 4 | z) << 4 | x;
    }

    public static int unpackX(int index) {
        return index & 0xF;
    }

    public static int unpackY(int index) {
        return index >> 8 & 0xF;
    }

    public static int unpackZ(int index) {
        return index >> 4 & 0xF;
    }
}
