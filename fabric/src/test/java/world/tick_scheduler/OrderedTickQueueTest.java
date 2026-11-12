package world.tick_scheduler;

import com.mojang.datafixers.util.Pair;
import net.caffeinemc.mods.lithium.common.world.scheduler.OrderedTickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({ "DataFlowIssue", "ConstantValue", "CodeBlock2Expr" })
public class OrderedTickQueueTest {


    @Test
    void queueWorksWithOneElement() {
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>();

        assertTrue(queue.isEmpty());
        queue.checkConsistency();
        assertFalse(queue.iterator().hasNext());

        queue.offer(tick(2));
        queue.offer(tick(1));
        queue.checkConsistency();
        queue.clear();
        assertTrue(queue.isEmpty());
        queue.checkConsistency();

        ScheduledTick<Object> tick = tick(-1);
        queue.offer(tick);
        queue.checkConsistency();
        assertEquals(-1, queue.peek().subTickOrder());
        queue.checkConsistency();

        assertEquals(tick, queue.poll());
        assertTrue(queue.isEmpty());
        queue.checkConsistency();

    }

    @Test
    void offerAfterPoll() {
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>();
        ScheduledTick<Object> tick1 = tick(1);
        ScheduledTick<Object> tick2 = tick(2);
        ScheduledTick<Object> tick3 = tick(3);

        queue.offer(tick3);
        queue.offer(tick1);

        assertSame(tick1, queue.poll());
        queue.offer(tick2);

        assertSame(tick2, queue.peek());
        assertSame(tick2, queue.poll());
        assertSame(tick3, queue.poll());
        queue.checkConsistency();
    }

    @Test
    void iteratorReturnsSortedTicksWithoutConsumingThem() {
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>();
        queue.offer(tick(3));
        queue.offer(tick(1));
        queue.offer(tick(2));

        List<Long> iteratedOrders = new ArrayList<>();
        for (ScheduledTick<Object> tick : queue) {
            queue.checkConsistency();
            iteratedOrders.add(tick.subTickOrder());
        }

        assertEquals(List.of(1L, 2L, 3L), iteratedOrders);
        assertEquals(3, queue.size());
        assertEquals(1, queue.peek().subTickOrder());
        assertEquals(1, queue.poll().subTickOrder());
        queue.checkConsistency();
    }

    @Test
    void iteratorThrowsWhenExhausted() {
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>();
        queue.offer(tick(1));
        Iterator<ScheduledTick<Object>> iterator = queue.iterator();

        assertTrue(iterator.hasNext());
        iterator.next();
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        queue.checkConsistency();
    }

    @Test
    void repeatedOfferAndPollReusesCapacity() throws ReflectiveOperationException {
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>(4);
        for (long order = 0; order < 2_000; order+=2) {
            ScheduledTick<Object> tick = tick(order);
            ScheduledTick<Object> tick2 = tick(order + 1);
            queue.offer(tick);
            queue.offer(tick2);
            assertSame(tick, queue.poll());
            assertSame(tick2, queue.poll());
            assertEquals(4, backingArray(queue).length);
        }
        queue.checkConsistency();

        for (long order = 2_000; order < 4_000; order+=2) {
            ScheduledTick<Object> tick = tick(order);
            ScheduledTick<Object> tick2 = tick(order + 1);
            queue.offer(tick2);
            queue.offer(tick);
            assertSame(tick, queue.poll());
            assertSame(tick2, queue.poll());
            assertEquals(4, backingArray(queue).length);
        }
        queue.checkConsistency();
    }

    @Test
    void removeIfCompactsQueue() throws ReflectiveOperationException {
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>(4);
        for (long order = 0; order < 1_000; order++) {
            ScheduledTick<Object> tick = tick(order);
            queue.offer(tick);
        }
        queue.checkConsistency();
        queue.removeIf(tick -> tick.subTickOrder() < 998);
        queue.checkConsistency();
        assertEquals(2, queue.size());
        assertTrue(32 > backingArray(queue).length);
        assertEquals(998, queue.peek().subTickOrder());
        assertEquals(998, queue.poll().subTickOrder());
        assertEquals(999, queue.peek().subTickOrder());
        assertEquals(999, queue.poll().subTickOrder());
        assertTrue(queue.isEmpty());
    }

    @Test
    void removeIfWithPredicateException() {
        for (int i = 0; i < 2; i++) {
            OrderedTickQueue<Object> queue = new OrderedTickQueue<>();
            queue.offer(tick(4));
            queue.offer(tick(1));
            queue.offer(tick(3));
            queue.offer(tick(2));

            if (i == 0) {
                queue.sort();
                queue.checkConsistency();
            }

            IllegalStateException failure = new IllegalStateException("predicate failed");
            int[] expectedSize = new int[] { queue.size() };

            assertSame(failure, assertThrows(IllegalStateException.class, () -> queue.removeIf(tick -> {
                if (tick.subTickOrder() == 3) {
                    throw failure;
                }
                if (tick.subTickOrder() == 1) {
                    expectedSize[0]--;
                    return true;
                } else {
                    return false;
                }
            })));

            queue.checkConsistency();

            int sizeExpected = expectedSize[0];
            assertEquals(sizeExpected, queue.size());
            if (queue.size() == 4) {
                assertEquals(1, queue.peek().subTickOrder());
                assertEquals(1, queue.poll().subTickOrder());
            }

            assertEquals(3, queue.size());
            assertEquals(2, queue.peek().subTickOrder());
            assertEquals(2, queue.poll().subTickOrder());
            assertEquals(3, queue.peek().subTickOrder());
            assertEquals(3, queue.poll().subTickOrder());
            assertEquals(4, queue.peek().subTickOrder());
            assertEquals(4, queue.poll().subTickOrder());
            assertNull(queue.peek());
            assertNull(queue.poll());
        }
    }

    @SuppressWarnings("unused")
    @Test
    void mixedOperationsMatchPriorityQueue() {
        Comparator<ScheduledTick<Object>> comparator = Comparator.comparingLong(ScheduledTick::subTickOrder);
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>(4);
        PriorityQueue<ScheduledTick<Object>> priorityQueue = new PriorityQueue<>(comparator);

        List<Pair<Integer, Consumer<Random>>> weightedOperations = List.of(
            Pair.of(1000,random -> {
                ScheduledTick<Object> tick = tick(random.nextInt(100) - 50L);
                assertTrue(queue.offer(tick));
                assertTrue(priorityQueue.offer(tick));
            }), Pair.of(1000, random -> {
                ScheduledTick<Object> tick = tick(random.nextInt(1_000_000));
                assertTrue(queue.offer(tick));
                assertTrue(priorityQueue.offer(tick));
            }), Pair.of(1, random -> {
                while (!priorityQueue.isEmpty() || !queue.isEmpty()) {
                    assertEquals(priorityQueue.poll(), queue.poll());
                }
                //noinspection ConstantValue
                assertEquals(priorityQueue.size(), queue.size());
                if (random.nextInt(10) == 0) {
                    queue.clear();
                    priorityQueue.clear();
                }
            }), Pair.of(333, random -> {
                assertEquals(priorityQueue.poll(), queue.poll());
            }), Pair.of(333, random -> {
                assertEquals(priorityQueue.peek(), queue.peek());
            }), Pair.of(10, random -> {
                queue.checkConsistency();
                queue.sort();
                queue.checkConsistency();
            }), Pair.of(20, random -> {
                int modulo = random.nextInt(30) + 3;
                queue.removeIf(tick -> tick.subTickOrder() % modulo == 0);
                priorityQueue.removeIf(tick -> tick.subTickOrder() % modulo == 0);
            }), Pair.of(1, random -> {
                for (int i = 0; i < 100; i++) {
                    assertEquals(priorityQueue.peek(), queue.peek());
                    assertEquals(priorityQueue.poll(), queue.poll());
                }
            })
        );

        int totalWeight = weightedOperations.stream().mapToInt(Pair::getFirst).sum();
        Random random = new Random();
        long seed = random.nextLong();
        random = new Random(seed);

        try {
            for (int i = 0; i < 100_000; i++) {
                int[] operation = { random.nextInt(totalWeight) };
                Consumer<Random> op = weightedOperations.stream().filter(pair -> (operation[0] -= pair.getFirst()) < 0).findFirst().orElseThrow().getSecond();
                op.accept(random);
            }
            queue.checkConsistency();
        } catch (Throwable t) {
            throw new AssertionError("Failed with seed " + seed, t);
        }
    }

    @Test
    void extremeSubTickOrders() {
        OrderedTickQueue<Object> queue = new OrderedTickQueue<>();
        queue.offer(tick(Long.MAX_VALUE));
        queue.offer(tick(0));
        queue.offer(tick(Long.MIN_VALUE));
        queue.offer(tick(Long.MAX_VALUE));


        assertEquals(Long.MIN_VALUE, queue.poll().subTickOrder());
        assertEquals(0, queue.poll().subTickOrder());
        assertEquals(Long.MAX_VALUE, queue.poll().subTickOrder());
        assertEquals(Long.MAX_VALUE, queue.poll().subTickOrder());
    }

    private static Object[] backingArray(OrderedTickQueue<?> queue) throws ReflectiveOperationException {
        Field field = OrderedTickQueue.class.getDeclaredField("arr");
        field.setAccessible(true);
        return (Object[]) field.get(queue);
    }

    private static final Object TICK_TYPE = new Object();

    private static ScheduledTick<Object> tick(long order) {
        return new ScheduledTick<>(TICK_TYPE, BlockPos.ZERO, 0L, TickPriority.NORMAL, order);
    }
    
}
