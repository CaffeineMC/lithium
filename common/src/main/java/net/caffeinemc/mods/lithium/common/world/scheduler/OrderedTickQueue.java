package net.caffeinemc.mods.lithium.common.world.scheduler;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.*;
import net.minecraft.world.ticks.ScheduledTick;

public class OrderedTickQueue<T> extends AbstractQueue<ScheduledTick<T>> {
    private static final int INITIAL_CAPACITY = 16;
    private static final Comparator<ScheduledTick<?>> COMPARATOR = Comparator.comparingLong(ScheduledTick::subTickOrder);

    private ScheduledTick<T>[] arr;

    private int lastIndexExclusive;
    private int firstIndex;

    private long currentMaxSubTickOrder = Long.MIN_VALUE;
    private boolean isSorted;
    private ScheduledTick<T> unsortedPeekResult;

    @SuppressWarnings("unchecked")
    public OrderedTickQueue(int capacity) {
        this.arr = (ScheduledTick<T>[]) new ScheduledTick[capacity];
        this.lastIndexExclusive = 0;
        this.isSorted = true;
        this.unsortedPeekResult = null;
        this.firstIndex = 0;
    }

    public OrderedTickQueue() {
        this(INITIAL_CAPACITY);
    }

    @Override
    public void clear() {
        Arrays.fill(this.arr, null);
        this.lastIndexExclusive = 0;
        this.firstIndex = 0;
        this.currentMaxSubTickOrder = Long.MIN_VALUE;
        this.isSorted = true;
        this.unsortedPeekResult = null;
    }

    @Override
    public Iterator<ScheduledTick<T>> iterator() {
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
        ScheduledTick<T> nextTick;
        int polledIndex = this.firstIndex++;
        ScheduledTick<T>[] ticks = this.arr;
        nextTick = ticks[polledIndex];
        ticks[polledIndex] = null;
        return nextTick;
    }

    @Override
    public ScheduledTick<T> peek() {
        if (!this.isSorted) {
            return this.unsortedPeekResult;
        } else if (this.lastIndexExclusive > this.firstIndex) {
            return this.getTickAtIndex(0);
        }
        return null;
    }

    public boolean offer(ScheduledTick<T> tick) {
        if (this.lastIndexExclusive >= this.arr.length) {
            //todo remove consumed elements first
            this.arr = copyArray(this.arr, HashCommon.nextPowerOfTwo(this.arr.length + 1));
        }
        if (tick.subTickOrder() <= this.currentMaxSubTickOrder) {
            //Set to unsorted instead of slowing down the insertion
            //This is rare but may happen in bulk
            //Sorting later needs O(n*log(n)) time, but it only needs to happen when unordered insertion needs to happen
            //Therefore it is better than n times log(n) time of the PriorityQueue that happens on ordered insertion too
            ScheduledTick<T> firstTick = this.isSorted ? this.size() > 0 ? this.arr[this.firstIndex] : null : this.unsortedPeekResult;
            this.isSorted = false;
            this.unsortedPeekResult = firstTick == null || tick.subTickOrder() < firstTick.subTickOrder() ? tick : firstTick;
        } else {
            this.currentMaxSubTickOrder = tick.subTickOrder();
        }
        this.arr[this.lastIndexExclusive++] = tick;
        return true;
    }

    public int size() {
        return this.lastIndexExclusive - this.firstIndex;
    }

    private void handleCompaction(int size) {
        // Only compact the array if it is less than 50% filled
        if (this.arr.length > INITIAL_CAPACITY && size < this.arr.length / 2) {
            this.arr = copyArray(this.arr, size);
        } else {
            // Fill the unused array elements with nulls to release our references to the elements in it
            Arrays.fill(this.arr, size, this.arr.length, null);
        }

        this.firstIndex = 0;
        this.lastIndexExclusive = size;

        if (size == 0 || !this.isSorted) {
            this.currentMaxSubTickOrder = Long.MIN_VALUE;
        } else {
            ScheduledTick<T> tick = this.arr[size - 1];
            this.currentMaxSubTickOrder = tick == null ? Long.MIN_VALUE : tick.subTickOrder();
        }
    }

    public void sort() {
        if (this.isSorted) {
            return;
        }
        this.removeNullsAndConsumed();
        Arrays.sort(this.arr, this.firstIndex, this.lastIndexExclusive, COMPARATOR);
        this.isSorted = true;
        this.unsortedPeekResult = null;
    }

    public void removeNullsAndConsumed() {
        int src = this.firstIndex;
        int dst = 0;
        while (src < this.lastIndexExclusive) {
            ScheduledTick<T> orderedTick = this.arr[src];
            if (orderedTick != null) {
                this.arr[dst] = orderedTick;
                dst++;
            }
            src++;
        }
        this.handleCompaction(dst);
    }

    public ScheduledTick<T> getTickAtIndex(int index) {
        if (!this.isSorted) {
            throw new IllegalStateException("Unexpected access on unsorted queue!");
        }
        return this.arr[this.firstIndex + index];
    }

    public void setTickAtIndex(int index, ScheduledTick<T> tick) {
        if (!this.isSorted) {
            throw new IllegalStateException("Unexpected access on unsorted queue!");
        }
        this.arr[this.firstIndex + index] = tick;
    }

    @SuppressWarnings("unchecked")
    private static <T> ScheduledTick<T>[] copyArray(ScheduledTick<T>[] src, int size) {
        final ScheduledTick<T>[] copy = new ScheduledTick[Math.max(INITIAL_CAPACITY, size)];

        if (size != 0) {
            System.arraycopy(src, 0, copy, 0, Math.min(src.length, size));
        }

        return copy;
    }

    @Override
    public boolean isEmpty() {
        return this.lastIndexExclusive <= this.firstIndex;
    }
}
