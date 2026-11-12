package net.caffeinemc.mods.lithium.common.world.scheduler;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Predicate;

public class OrderedTickQueue<T> extends AbstractQueue<ScheduledTick<T>> {
    private static final int INITIAL_CAPACITY = 16;
    private static final Comparator<ScheduledTick<?>> COMPARATOR = Comparator.comparingLong(ScheduledTick::subTickOrder);

    private ScheduledTick<T>[] arr;

    private int lastIndexExclusive;
    private int firstIndex;

    private long maxSeenSubtickOrder;
    private boolean isSorted;
    private ScheduledTick<T> unsortedPeekResult;

    @SuppressWarnings("unchecked")
    public OrderedTickQueue(int capacity) {
        this.arr = (ScheduledTick<T>[]) new ScheduledTick[capacity];
        this.initEmpty();
    }

    public OrderedTickQueue() {
        this(INITIAL_CAPACITY);
    }

    @Override
    public void clear() {
        Arrays.fill(this.arr, null);
        this.initEmpty();
    }

    private void initEmpty() {
        this.lastIndexExclusive = 0;
        this.firstIndex = 0;
        this.maxSeenSubtickOrder = Long.MIN_VALUE;
        this.isSorted = true;
        this.unsortedPeekResult = null;
    }

    @Override
    public @NonNull Iterator<ScheduledTick<T>> iterator() {
        if (this.isEmpty()) {
            return Collections.emptyIterator();
        }
        this.sort();
        return new Iterator<>() {
            int nextIndex = OrderedTickQueue.this.firstIndex;

            @Override
            public boolean hasNext() {
                return this.nextIndex < OrderedTickQueue.this.lastIndexExclusive;
            }

            @Override
            public ScheduledTick<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return OrderedTickQueue.this.arr[this.nextIndex++];
            }
        };
    }

    @Override
    public ScheduledTick<T> poll() {
        if (this.isEmpty()) {
            return null;
        }
        if (!this.isSorted) {
            this.sort();
        }
        int polledIndex = this.firstIndex++;
        ScheduledTick<T>[] ticks = this.arr;
        ScheduledTick<T> nextTick = ticks[polledIndex];
        ticks[polledIndex] = null;
        return nextTick;
    }

    @Override
    public ScheduledTick<T> peek() {
        if (!this.isSorted) {
            return this.unsortedPeekResult;
        } else if (this.lastIndexExclusive > this.firstIndex) {
            return this.getTickAtIndex(this.firstIndex);
        }
        return null;
    }

    public boolean offer(ScheduledTick<T> tick) {
        if (this.lastIndexExclusive >= this.arr.length) {
            if (this.firstIndex > 0) {
                this.compact();
            } else {
                this.arr = copyArray(this.arr, HashCommon.nextPowerOfTwo(this.arr.length + 1));
            }
        }
        if (tick.subTickOrder() > this.maxSeenSubtickOrder) {
            //No need to sort again if append at end is in correct order
            this.maxSeenSubtickOrder = tick.subTickOrder();
        } else {
            if (this.isSorted) {
                //Set to unsorted instead of slowing down the insertion
                //This is rare but may happen in bulk
                //Sorting later needs O(n*log(n)) time, but it only needs to happen with unordered insertions
                this.unsortedPeekResult = this.isEmpty() ? null : this.arr[this.firstIndex];
                this.isSorted = false;
            }
            if (this.unsortedPeekResult == null || tick.subTickOrder() < this.unsortedPeekResult.subTickOrder()) {
                this.unsortedPeekResult = tick;
            }
        }
        this.arr[this.lastIndexExclusive++] = tick;
        return true;
    }

    @Override
    public int size() {
        return this.lastIndexExclusive - this.firstIndex;
    }

    @Override
    public boolean isEmpty() {
        return this.lastIndexExclusive <= this.firstIndex;
    }

    private void compact() {
        int size = this.size();
        if (this.firstIndex != 0 || this.lastIndexExclusive != size) {
            System.arraycopy(this.arr, this.firstIndex, this.arr, 0, size);
            Arrays.fill(this.arr, size, this.arr.length, null);
            this.firstIndex = 0;
            this.lastIndexExclusive = size;
        } else if (this.arr.length > INITIAL_CAPACITY && size < this.arr.length / 2) {
            // Only compact the array if it is less than 50% filled
            this.arr = copyArray(this.arr, size);
        } else {
            // Fill the unused array elements with nulls to release our references to the elements in it
            Arrays.fill(this.arr, size, this.arr.length, null);
        }

        if (this.isEmpty()) {
            this.initEmpty();
        }
    }

    public void sort() {
        if (this.isSorted) {
            return;
        }
        Arrays.sort(this.arr, this.firstIndex, this.lastIndexExclusive, COMPARATOR);

        //As removal doesn't lower maxSeenSubtickOrder, updating it here may be beneficial
        this.maxSeenSubtickOrder = this.isEmpty() ? Long.MIN_VALUE : this.arr[this.lastIndexExclusive - 1].subTickOrder();

        this.isSorted = true;
        this.unsortedPeekResult = null;
    }

    private ScheduledTick<T> getTickAtIndex(int index) {
        if (!this.isSorted) {
            throw new IllegalStateException("Unexpected access on unsorted queue!");
        }
        return this.arr[index];
    }

    @Override
    public boolean removeIf(@NonNull Predicate<? super ScheduledTick<T>> filter) {
        Objects.requireNonNull(filter);
        int size = this.size();
        int src = this.firstIndex;
        int dst = 0;

        ScheduledTick<T> minTick = null;
        try {
            while (src < this.lastIndexExclusive) {
                ScheduledTick<T> orderedTick = this.arr[src];
                if (!filter.test(orderedTick)) {
                    if (!this.isSorted && (minTick == null || orderedTick.subTickOrder() < minTick.subTickOrder())) {
                        minTick = orderedTick;
                    }
                    this.arr[dst] = orderedTick;
                    dst++;
                }
                src++;
            }
        } catch (Exception e) {
            this.fixCollectionOnThrowDuringRemoveIf(src, dst, minTick);
            throw e;
        }

        if (!this.isSorted) {
            this.unsortedPeekResult = minTick;
        }
        this.firstIndex = 0;
        this.lastIndexExclusive = dst;

        this.compact();
        return size != this.size();
    }

    @SuppressWarnings("unchecked")
    private static <T> ScheduledTick<T>[] copyArray(ScheduledTick<T>[] src, int size) {
        final ScheduledTick<T>[] copy = new ScheduledTick[Math.max(INITIAL_CAPACITY, size)];

        if (size != 0) {
            System.arraycopy(src, 0, copy, 0, Math.min(src.length, size));
        }

        return copy;
    }

    private void fixCollectionOnThrowDuringRemoveIf(int remainingStart, int dst, ScheduledTick<T> processedMinTick) {
        //In case the predicate throws an exception, the queue must not become inconsistent.
        // The remaining elements are not filtered.
        int remainingCount = this.lastIndexExclusive - remainingStart;
        System.arraycopy(this.arr, remainingStart, this.arr, dst, remainingCount);
        this.firstIndex = 0;
        this.lastIndexExclusive = dst + remainingCount;

        //Finish searching for the minimum tick avoid an inconsistent peek result
        if (!this.isSorted) {
            ScheduledTick<T> minTick = processedMinTick;
            for (int i = dst; i < this.lastIndexExclusive; i++) {
                ScheduledTick<T> orderedTick = this.arr[i];
                if (minTick == null || orderedTick.subTickOrder() < minTick.subTickOrder()) {
                    minTick = orderedTick;
                }
            }
            this.unsortedPeekResult = minTick;
        }

        if (this.isEmpty()) {
            this.initEmpty();
        }
    }

    public void checkConsistency() {
        //noinspection ConstantValue
        if (this.size() < 0) {
            throw new IllegalStateException("Queue size is" + this.size() + " but should be >= 0!");
        }
        for (int i = 0; i < this.firstIndex; i++) {
            if (this.arr[i] != null) {
                throw new IllegalStateException("Array elements before firstIndex should be null, but found non-null element " + this.arr[i] + " at index " + i);
            }
        }
        for (int i = this.lastIndexExclusive; i < this.arr.length; i++) {
            if (this.arr[i] != null) {
                throw new IllegalStateException("Array elements after lastIndexExclusive should be null, but found non-null element " + this.arr[i] + " at index " + i);
            }
        }
        for (int i = this.firstIndex; i < this.lastIndexExclusive; i++) {
            if (this.arr[i] == null) {
                throw new IllegalStateException("Array elements between firstIndex and lastIndexExclusive should not be null, but found null element at index " + i);
            }
            if (this.arr[i].subTickOrder() > this.maxSeenSubtickOrder) {
                throw new IllegalStateException("Elements should not have a subTickOrder higher than maxSeenSubtickOrder, but found element " + this.arr[i] + " at index " + i);
            }
        }

        if (this.isSorted) {
            if (this.unsortedPeekResult != null) {
                throw new IllegalStateException("Unsorted peek result should be null when sorted, but found " + this.unsortedPeekResult);
            }
            ScheduledTick<T> prev = null;
            for (int i = this.firstIndex + 1; i < this.lastIndexExclusive; i++) {
                ScheduledTick<T> tick = this.arr[i];
                if (prev != null && tick.subTickOrder() < prev.subTickOrder()) {
                    throw new IllegalStateException("Queue is not sorted, found element " + tick + " at index " + i + " with subTickOrder " + tick.subTickOrder() + " after element " + prev + " with subTickOrder " + prev.subTickOrder());
                }
                prev = tick;
            }

        } else {
            if (this.unsortedPeekResult == null) {
                throw new IllegalStateException("Unsorted peek result should not be null when unsorted!");
            }
            ScheduledTick<T> minTick = null;
            for (int i = this.firstIndex; i < this.lastIndexExclusive; i++) {
                ScheduledTick<T> tick = this.arr[i];
                if (minTick == null || tick.subTickOrder() < minTick.subTickOrder()) {
                    minTick = tick;
                }
            }
            if (minTick != this.unsortedPeekResult) {
                throw new IllegalStateException("Unsorted peek result is not the minimum tick, expected " + minTick + " but found " + this.unsortedPeekResult);
            }
        }
    }
}
