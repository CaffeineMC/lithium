package world.tick_scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TickSchedulerTest {

    @Test
    void clearAreaAfterTickBudgetLeavesNoTicks() {
        LevelTicks<Object> ticks = new LevelTicks<>(chunk -> true);
        LevelChunkTicks<Object> container = new LevelChunkTicks<>();
        ticks.addContainer(new ChunkPos(0, 0), container);

        ticks.schedule(tick(2, 2));
        ticks.schedule(tick(0, 0));
        ticks.schedule(tick(1, 1));

        ticks.tick(1L, 1, (_, _) -> {}); // at most one tick: the (1, NORMAL) bucket is left partly polled
        ticks.clearArea(new BoundingBox(0, 0, 0, 15, 0, 15)); // NullPointerException with Lithium 0.26.3; vanilla removes the two ticks

        assertEquals(0, ticks.count());
        assertNull(container.peek());
    }

    private static final Object TICK_TYPE = new Object();
    private static ScheduledTick<Object> tick(long subTickOrder, int xPos) {
        return new ScheduledTick<>(TICK_TYPE, new BlockPos(xPos, 0, 0), 0L, TickPriority.NORMAL, subTickOrder);
    }

}
