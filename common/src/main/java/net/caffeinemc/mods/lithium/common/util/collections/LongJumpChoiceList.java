package net.caffeinemc.mods.lithium.common.util.collections;

import it.unimi.dsi.fastutil.bytes.ByteBytePair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos;

/**
 * This class represents a list of potential targets for a long jump task. It only implements the List interface, so
 * it can work with vanilla's code, but it doesn't implement the List interface strictly.
 * It is optimized for quick weighted random choice and removal of elements based on their squared distance.
 * <p>
 * Vanilla's implementation comes with the following issues:
 * Random choice requires calculating the weight sum by iterating all elements.
 * Random choice requires summing all weights until exceeding the randomly picked weight.
 * Random removal requires an array copy (~400 elements for frog jumps, >1000 elements for goat jumps)
 * Each of these is done for each element if there is no early exit (a valid jump)
 * <p>
 * This implementation keeps an up-to-date total weight, allows random choice with fewer addition operations.
 * Removal is done quickly by swapping the removed element to the end, disregarding the order of elements.
 */
public class LongJumpChoiceList extends AbstractList<LongJumpToRandomPos.PossibleJump> {

    /**
     * A cache of choice lists for different ranges. The elements must not be mutated, but copied instead.
     * In vanilla minecraft there should be two elements, one for frog jumps and one for goat jumps.
     */
    private static final ConcurrentHashMap<ByteBytePair, LongJumpChoiceList> CHOICE_LISTS = new ConcurrentHashMap<>();
    /**
     * The choice list for frog jumps. Skipping the hash map access. Must not be mutated, but copied instead.
     */
    private static final LongJumpChoiceList FROG_JUMP = new LongJumpChoiceList((byte) 4, (byte) 2);
    /**
     * The choice list for goat jumps. Skipping the hash map access. Must not be mutated, but copied instead.
     */
    private static final LongJumpChoiceList GOAT_JUMP = new LongJumpChoiceList((byte) 5, (byte) 5);


    private final BlockPos origin;
    private final IntArrayList[] packedOffsetsByDistanceSq;
    private final int[] weightByDistanceSq;
    private int totalWeight;

    /**
     * Constructs a new LongJumpChoiceList with the given horizontal and vertical range.
     * We avoid creating too many objects here, e.g. LongJumpTask.Target is not created yet.
     * @param horizontalRange the horizontal range
     * @param verticalRange the vertical range
     */
    public LongJumpChoiceList(byte horizontalRange, byte verticalRange) {
        if (horizontalRange < 0 || verticalRange < 0) {
            throw new IllegalArgumentException("The ranges must be within 0..127!");
        }

        this.origin = BlockPos.ZERO;
        int maxSqDistance = horizontalRange*horizontalRange * 2 + verticalRange*verticalRange;
        this.packedOffsetsByDistanceSq = new IntArrayList[maxSqDistance];
        this.weightByDistanceSq = new int[maxSqDistance];

        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    int squaredDistance = x * x + y * y + z * z;
                    int index = squaredDistance - 1;
                    if (index >= 0) { //exclude origin (distance 0)
                        int packedOffset = this.packOffset(x, y, z);
                        IntArrayList offsets = this.packedOffsetsByDistanceSq[index];
                        if (offsets == null) {
                            this.packedOffsetsByDistanceSq[index] = offsets = new IntArrayList();
                        }
                        offsets.add(packedOffset);
                        this.weightByDistanceSq[index] += squaredDistance;
                        this.totalWeight += squaredDistance;
                    }
                }
            }
        }
    }

    public LongJumpChoiceList(BlockPos origin, IntArrayList[] packedOffsetsByDistanceSq, int[] weightByDistanceSq, int totalWeight) {
        this.origin = origin;
        this.packedOffsetsByDistanceSq = packedOffsetsByDistanceSq;
        this.weightByDistanceSq = weightByDistanceSq;
        this.totalWeight = totalWeight;
    }

    private int packOffset(int x, int y, int z) {
        return (x + 128) | ((y + 128) << 8) | ((z + 128) << 16);
    }

    private int unpackX(int packedOffset) {
        return (packedOffset & 0xFF) - 128;
    }

    private int unpackY(int packedOffset) {
        return ((packedOffset >>> 8) & 0xFF) - 128;
    }

    private int unpackZ(int packedOffset) {
        return ((packedOffset >>> 16) & 0xFF) - 128;
    }

    /**
     * Returns a LongJumpChoiceList for the given center position and ranges.
     * Quickly creates the list by copying an existing, memoized list.
     * @param centerPos the center position
     * @param horizontalRange the horizontal range
     * @param verticalRange the vertical range
     * @return a LongJumpChoiceList for the given parameters
     */
    public static LongJumpChoiceList forCenter(BlockPos centerPos, byte horizontalRange, byte verticalRange) {
        if (horizontalRange < 0 || verticalRange < 0) {
            throw new IllegalArgumentException("The ranges must be within 0..127!");
        }

        LongJumpChoiceList jumpDestinationsList;
        short range = (short) ((horizontalRange << 8) | verticalRange);
        if (range == ((4 << 8) | 2)) {
            //Frog jump
            jumpDestinationsList = LongJumpChoiceList.FROG_JUMP;
        } else if (range == ((5 << 8) | 5)) {
            //Goat jump
            jumpDestinationsList = LongJumpChoiceList.GOAT_JUMP;
        } else {
            jumpDestinationsList = LongJumpChoiceList.CHOICE_LISTS.computeIfAbsent(
                    ByteBytePair.of(horizontalRange, verticalRange),
                    key -> new LongJumpChoiceList(key.leftByte(), key.rightByte())
            );
        }

        return jumpDestinationsList.offsetCopy(centerPos);
    }

    private LongJumpChoiceList offsetCopy(BlockPos offset) {
        IntArrayList[] packedOffsetsByDistanceSq = new IntArrayList[this.packedOffsetsByDistanceSq.length];
        for (int i = 0; i < packedOffsetsByDistanceSq.length; i++) {
            IntArrayList packedOffsets = this.packedOffsetsByDistanceSq[i];
            if (packedOffsets != null) {
                packedOffsetsByDistanceSq[i] = packedOffsets.clone();
            }
        }

        return new LongJumpChoiceList(
                this.origin.offset(offset),
                packedOffsetsByDistanceSq,
                Arrays.copyOf(this.weightByDistanceSq, this.weightByDistanceSq.length), this.totalWeight);
    }

    /**
     * Removes and returns a random target from the list, weighted by squared distance.
     * @param random the random number generator
     * @return a random target
     */
    public LongJumpToRandomPos.PossibleJump removeRandomWeightedByDistanceSq(RandomSource random) {
        if (this.totalWeight <= 0) {
            //vanilla's WeightedRandom.getRandomItem returns Optional.empty() for a total weight of 0
            return null;
        }
        int targetWeight = random.nextInt(this.totalWeight);
        for (int index = 0; targetWeight >= 0 && index < this.weightByDistanceSq.length; index++) {
            targetWeight -= this.weightByDistanceSq[index];
            if (targetWeight < 0) {
                int distanceSq = index + 1;
                IntArrayList elementsOfDistance = this.packedOffsetsByDistanceSq[index];
                int elementIndex = random.nextInt(elementsOfDistance.size());

                //fast remove by swapping to end and removing, order does not matter
                elementsOfDistance.set(elementIndex, elementsOfDistance.set(elementsOfDistance.size() - 1, elementsOfDistance.getInt(elementIndex)));
                int packedOffset = elementsOfDistance.removeInt(elementsOfDistance.size() - 1);
                this.weightByDistanceSq[index] -= distanceSq;
                this.totalWeight -= distanceSq;

                return new LongJumpToRandomPos.PossibleJump(this.origin.offset(this.unpackX(packedOffset), this.unpackY(packedOffset), this.unpackZ(packedOffset)), distanceSq);
            }
        }
        return null;
    }

    @Override
    public LongJumpToRandomPos.PossibleJump get(int index) {
        int elementIndex = index;
        IntArrayList[] offsetsByDistanceSq = this.packedOffsetsByDistanceSq;
        for (int arrayIndex = 0; arrayIndex < offsetsByDistanceSq.length; arrayIndex++) {
            IntArrayList packedOffsets = offsetsByDistanceSq[arrayIndex];
            if (packedOffsets != null) {
                if (elementIndex < packedOffsets.size()) {
                    int distanceSq = arrayIndex + 1;
                    int packedOffset = packedOffsets.getInt(elementIndex);
                    return new LongJumpToRandomPos.PossibleJump(this.origin.offset(this.unpackX(packedOffset), this.unpackY(packedOffset), this.unpackZ(packedOffset)), distanceSq);
                }
                elementIndex -= packedOffsets.size();
            }
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public boolean isEmpty() {
        return this.totalWeight == 0;
    }

    @Override
    public int size() {
        int size = 0;
        for (IntArrayList packedOffsets : this.packedOffsetsByDistanceSq) {
            if (packedOffsets != null) {
                size += packedOffsets.size();
            }
        }
        return size;
    }

    @Override
    public LongJumpToRandomPos.PossibleJump remove(int index) {
        int elementIndex = index;
        IntArrayList[] offsetsByDistanceSq = this.packedOffsetsByDistanceSq;
        for (int arrayIndex = 0; arrayIndex < offsetsByDistanceSq.length; arrayIndex++) {
            IntArrayList packedOffsets = offsetsByDistanceSq[arrayIndex];
            if (packedOffsets != null) {
                if (elementIndex < packedOffsets.size()) {
                    int distanceSq = arrayIndex + 1;
                    int packedOffset = packedOffsets.getInt(elementIndex);
                    packedOffsets.set(elementIndex, packedOffsets.set(packedOffsets.size() - 1, packedOffsets.getInt(elementIndex)));
                    packedOffsets.removeInt(packedOffsets.size() - 1);
                    this.weightByDistanceSq[arrayIndex] -= distanceSq;
                    this.totalWeight -= distanceSq;
                    return new LongJumpToRandomPos.PossibleJump(this.origin.offset(this.unpackX(packedOffset), this.unpackY(packedOffset), this.unpackZ(packedOffset)), distanceSq);
                }
                elementIndex -= packedOffsets.size();
            }
        }
        throw new IndexOutOfBoundsException();
    }
}
