package com.norbertfila.fluid;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.BlockPos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FluidSchedulerTest {
    private static final BlockPos FIRST = new BlockPos(-1, -17, 16);
    private static final BlockPos SECOND = new BlockPos(16, 0, -1);
    private static final BlockPos THIRD = new BlockPos(0, 0, 0);

    @Test
    void executesOnlyDueTasksInDeadlineThenAdmissionOrder() {
        FluidScheduler scheduler = new FluidScheduler();
        scheduler.schedule(FIRST, 20);
        scheduler.schedule(SECOND, 10);
        scheduler.schedule(THIRD, 10);
        List<BlockPos> executed = new ArrayList<>();

        assertEquals(0, scheduler.processDue(9, executed::add));
        assertEquals(2, scheduler.processDue(10, executed::add));
        assertEquals(List.of(SECOND, THIRD), executed);
        assertEquals(1, scheduler.size());
        assertEquals(1, scheduler.processDue(20, executed::add));
        assertEquals(List.of(SECOND, THIRD, FIRST), executed);
        assertEquals(0, scheduler.size());
    }

    @Test
    void duplicateAndLaterRequestsDoNotPostponeOrReorderTasks() {
        FluidScheduler scheduler = new FluidScheduler();
        assertTrue(scheduler.schedule(FIRST, 15));
        scheduler.schedule(SECOND, 15);
        assertFalse(scheduler.schedule(new BlockPos(-1, -17, 16), 15));
        assertFalse(scheduler.schedule(FIRST, 30));
        assertEquals(2, scheduler.size());
        List<BlockPos> executed = new ArrayList<>();
        scheduler.processDue(15, executed::add);
        assertEquals(List.of(FIRST, SECOND), executed);
        assertEquals(0, scheduler.processDue(30, executed::add));
    }

    @Test
    void earlierRequestReplacesItsOldDeadlineAndReceivesNewAdmissionOrder() {
        FluidScheduler scheduler = new FluidScheduler();
        scheduler.schedule(FIRST, 30);
        scheduler.schedule(SECOND, 10);
        assertTrue(scheduler.schedule(FIRST, 10));
        assertEquals(2, scheduler.size());
        List<BlockPos> executed = new ArrayList<>();
        assertEquals(2, scheduler.processDue(10, executed::add));
        assertEquals(List.of(SECOND, FIRST), executed);
        assertEquals(0, scheduler.processDue(30, executed::add));
    }

    @Test
    void repeatedAccelerationStillExecutesOneUpdate() {
        FluidScheduler scheduler = new FluidScheduler();
        for (int deadline = 10_000; deadline >= 1; deadline--) assertTrue(scheduler.schedule(FIRST, deadline));
        assertEquals(1, scheduler.size());
        List<BlockPos> executed = new ArrayList<>();
        assertEquals(1, scheduler.processDue(1, executed::add));
        assertEquals(0, scheduler.processDue(10_000, executed::add));
        assertEquals(List.of(FIRST), executed);
        assertEquals(0, scheduler.size());
    }

    @Test
    void budgetPersistsAcrossCallsInTheSameTickAndBacklogKeepsItsOrder() {
        FluidScheduler scheduler = new FluidScheduler(2);
        scheduler.schedule(FIRST, 5);
        scheduler.schedule(SECOND, 5);
        scheduler.schedule(THIRD, 5);
        List<BlockPos> executed = new ArrayList<>();
        assertEquals(2, scheduler.processDue(5, executed::add));
        assertEquals(0, scheduler.processDue(5, executed::add));
        assertEquals(1, scheduler.size());
        scheduler.schedule(FIRST, 6);
        assertEquals(2, scheduler.processDue(6, executed::add));
        assertEquals(List.of(FIRST, SECOND, THIRD, FIRST), executed);
        assertEquals(0, scheduler.size());
    }

    @Test
    void defaultBudgetCarriesExcessWorkIntoTheNextTick() {
        FluidScheduler scheduler = new FluidScheduler();
        for (int x = 0; x < 2_003; x++) scheduler.schedule(new BlockPos(x, 0, 0), 1);
        List<BlockPos> executed = new ArrayList<>();
        assertEquals(2_000, scheduler.processDue(1, executed::add));
        assertEquals(3, scheduler.size());
        assertEquals(3, scheduler.processDue(2, executed::add));
        for (int x = 0; x < 2_003; x++) assertEquals(new BlockPos(x, 0, 0), executed.get(x));
    }

    @Test
    void callbackCanRescheduleItsOwnPositionWithoutLosingTheNewTick() {
        FluidScheduler scheduler = new FluidScheduler();
        scheduler.scheduleWater(FIRST, 0);
        assertEquals(1, scheduler.processDue(15, position -> assertTrue(scheduler.scheduleWater(position, 15))));
        assertEquals(1, scheduler.size());
        assertEquals(0, scheduler.processDue(29, ignored -> fail()));
        assertEquals(1, scheduler.processDue(30, position -> assertEquals(FIRST, position)));
    }

    @Test
    void immediateSelfReschedulingCannotBypassTheTickBudget() {
        FluidScheduler scheduler = new FluidScheduler(3);
        scheduler.schedule(FIRST, 0);
        assertEquals(3, scheduler.processDue(0, position -> scheduler.schedule(position, 0)));
        assertEquals(0, scheduler.processDue(0, ignored -> fail()));
        assertEquals(1, scheduler.size());
        assertEquals(1, scheduler.processDue(1, ignored -> {}));
    }

    @Test
    void callbackCanScheduleNeighborsInDeterministicOrder() {
        FluidScheduler scheduler = new FluidScheduler();
        scheduler.scheduleWater(FIRST, 0);
        scheduler.processDue(15, ignored -> {
            scheduler.scheduleWater(THIRD, 15);
            scheduler.scheduleWater(SECOND, 15);
        });
        List<BlockPos> executed = new ArrayList<>();
        scheduler.processDue(30, executed::add);
        assertEquals(List.of(THIRD, SECOND), executed);
    }

    @Test
    void failedUpdateIsRetriedWithoutLosingItsPlaceOrResettingTheBudget() {
        FluidScheduler scheduler = new FluidScheduler(1);
        scheduler.schedule(FIRST, 0);
        scheduler.schedule(SECOND, 0);
        IllegalStateException failure = new IllegalStateException("update failed");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> scheduler.processDue(0, ignored -> {
                            throw failure;
                        })));
        assertEquals(2, scheduler.size());
        assertEquals(0, scheduler.processDue(0, ignored -> fail()));
        List<BlockPos> executed = new ArrayList<>();
        scheduler.processDue(1, executed::add);
        scheduler.processDue(2, executed::add);
        assertEquals(List.of(FIRST, SECOND), executed);
    }

    @ParameterizedTest
    @ValueSource(longs = {5, 10, 20})
    void failureRetainsTheEarliestDeadlineIfCallbackAlreadyRescheduled(long replacementDeadline) {
        FluidScheduler scheduler = new FluidScheduler(1);
        scheduler.schedule(FIRST, 10);
        assertThrows(
                IllegalStateException.class,
                () -> scheduler.processDue(10, position -> {
                    scheduler.schedule(position, replacementDeadline);
                    throw new IllegalStateException("partial update failed");
                }));
        assertEquals(1, scheduler.size());
        assertEquals(1, scheduler.processDue(11, position -> assertEquals(FIRST, position)));
        assertEquals(0, scheduler.processDue(20, ignored -> fail()));
    }

    @Test
    void recursiveProcessingCannotAdvanceTheClockOrConsumeOtherTasks() {
        FluidScheduler scheduler = new FluidScheduler(1);
        scheduler.schedule(FIRST, 0);
        assertThrows(
                IllegalStateException.class,
                () -> scheduler.processDue(0, ignored -> scheduler.processDue(100, nested -> fail())));
        assertEquals(1, scheduler.size());
        assertEquals(1, scheduler.processDue(1, ignored -> {}));
    }

    @Test
    void invalidArgumentsLeavePendingWorkAndTickBudgetIntact() {
        assertThrows(IllegalArgumentException.class, () -> new FluidScheduler(0));
        assertThrows(IllegalArgumentException.class, () -> new FluidScheduler(-1));
        FluidScheduler scheduler = new FluidScheduler(1);
        scheduler.schedule(FIRST, 15);
        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(FIRST, -1));
        assertThrows(IllegalArgumentException.class, () -> scheduler.scheduleWater(FIRST, -1));
        assertThrows(ArithmeticException.class, () -> scheduler.scheduleWater(FIRST, Long.MAX_VALUE));
        assertThrows(NullPointerException.class, () -> scheduler.schedule(null, 0));
        assertThrows(IllegalArgumentException.class, () -> scheduler.processDue(-1, ignored -> {}));
        assertThrows(NullPointerException.class, () -> scheduler.processDue(100, null));
        assertEquals(0, scheduler.processDue(14, ignored -> fail()));
        assertThrows(IllegalArgumentException.class, () -> scheduler.processDue(13, ignored -> {}));
        assertEquals(1, scheduler.size());
        assertEquals(1, scheduler.processDue(15, position -> assertEquals(FIRST, position)));
    }

    @Test
    void supportsFullIntegerPositionsAndLastRepresentableDeadline() {
        FluidScheduler scheduler = new FluidScheduler();
        BlockPos minimum = new BlockPos(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);
        BlockPos maximum = new BlockPos(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        scheduler.scheduleWater(minimum, Long.MAX_VALUE - FluidScheduler.WATER_TICK_DELAY);
        scheduler.schedule(maximum, Long.MAX_VALUE);
        List<BlockPos> executed = new ArrayList<>();
        assertEquals(0, scheduler.processDue(Long.MAX_VALUE - 1, executed::add));
        assertEquals(2, scheduler.processDue(Long.MAX_VALUE, executed::add));
        assertEquals(List.of(minimum, maximum), executed);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 120, 240})
    void waterDelayUsesWorldTicksAtEveryFrameRateWithoutAPlayer(int framesPerSecond) {
        FixedSimulation clock = new FixedSimulation(FixedSimulation.TICK_SECONDS, 5);
        FluidScheduler scheduler = new FluidScheduler();
        scheduler.scheduleWater(FIRST, clock.tickCount());
        List<Long> executedAt = new ArrayList<>();
        for (int frame = 0; frame < framesPerSecond; frame++) {
            clock.advance(
                    1.0 / framesPerSecond,
                    ignored -> scheduler.processDue(clock.tickCount(), position -> {
                        executedAt.add(clock.tickCount());
                        scheduler.scheduleWater(position, clock.tickCount());
                    }));
        }
        assertEquals(60, clock.tickCount());
        assertEquals(List.of(15L, 30L, 45L, 60L), executedAt);
        assertEquals(.25, FluidScheduler.WATER_TICK_DELAY * FixedSimulation.TICK_SECONDS);
        assertEquals(1, scheduler.size());
    }

    @Test
    void pendingInListsOnlyThatChunkInExecutionOrderAndTracksRemovals() {
        FluidScheduler scheduler = new FluidScheduler();
        scheduler.schedule(new BlockPos(3, 1, 1), 20);
        scheduler.schedule(new BlockPos(-1, 1, 1), 5);
        scheduler.schedule(new BlockPos(5, 1, 1), 10);
        scheduler.schedule(new BlockPos(5, 1, 1), 4);

        var chunk = new com.norbertfila.world.ChunkPos(0, 0, 0);
        assertEquals(
                java.util.List.of(new BlockPos(5, 1, 1), new BlockPos(3, 1, 1)),
                scheduler.pendingIn(chunk).stream()
                        .map(FluidScheduler.PendingUpdate::position)
                        .toList());
        scheduler.cancel(new BlockPos(3, 1, 1));
        scheduler.processDue(4, ignored -> {});
        assertTrue(scheduler.pendingIn(chunk).isEmpty());
        assertEquals(
                1,
                scheduler
                        .pendingIn(new com.norbertfila.world.ChunkPos(-1, 0, 0))
                        .size());
    }
}
