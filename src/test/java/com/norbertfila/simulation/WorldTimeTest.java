package com.norbertfila.simulation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldTimeTest {
    @Test
    void startsAtSunriseWithTheCycleEnabled() {
        assertEquals(0, WorldTime.INITIAL.tickOfDay());
        assertEquals(0, WorldTime.INITIAL.minecraftTime());
        assertTrue(WorldTime.INITIAL.cycleEnabled());
    }

    @Test
    void completesExactlyOneDayInTwentyMinutesOfSimulationTicks() {
        assertEquals(72000, WorldTime.TICKS_PER_DAY);
        assertEquals(1200 * FixedSimulation.TICKS_PER_SECOND, WorldTime.TICKS_PER_DAY);
        WorldTime time = WorldTime.INITIAL;
        for (int tick = 1; tick < WorldTime.TICKS_PER_DAY; tick++) {
            time = time.tick();
            assertEquals(tick, time.tickOfDay());
        }
        assertEquals(WorldTime.INITIAL, time.tick());
        assertEquals(new WorldTime(1, true), time.tick().tick());
    }

    @Test
    void freezingAndResumingPreserveSubMinecraftTickPrecision() {
        var initial = new WorldTime(18001, true);
        var frozen = initial.withCycleEnabled(false);
        assertEquals(18001, frozen.tickOfDay());
        for (int tick = 0; tick < WorldTime.TICKS_PER_DAY; tick++) frozen = frozen.tick();
        assertEquals(new WorldTime(18001, false), frozen);
        assertEquals(new WorldTime(18002, true), frozen.withCycleEnabled(true).tick());
        assertEquals(new WorldTime(18001, true), initial);
    }

    @Test
    void settingTimePreservesTheCycleRuleAndConvertsAllMinecraftValuesExactly() {
        for (boolean enabled : new boolean[] {false, true}) {
            var original = new WorldTime(42, enabled);
            for (int minecraftTime = 0; minecraftTime < WorldTime.MINECRAFT_TICKS_PER_DAY; minecraftTime++) {
                var changed = original.withMinecraftTime(minecraftTime);
                assertEquals(minecraftTime * 3, changed.tickOfDay());
                assertEquals(minecraftTime, changed.minecraftTime());
                assertEquals(enabled, changed.cycleEnabled());
            }
            assertEquals(new WorldTime(42, enabled), original);
        }
    }

    @Test
    void queryingMinecraftTimeTruncatesOnlyTheDisplayValue() {
        assertEquals(0, new WorldTime(1, true).minecraftTime());
        assertEquals(0, new WorldTime(2, true).minecraftTime());
        assertEquals(1, new WorldTime(3, true).minecraftTime());
        assertEquals(23999, new WorldTime(71999, true).minecraftTime());
    }

    @Test
    void dayLandmarksMatchTheSimulationRate() {
        for (int minecraftTime : new int[] {0, 6000, 12000, 18000}) {
            var time = WorldTime.INITIAL.withMinecraftTime(minecraftTime);
            assertEquals(minecraftTime / 20, time.tickOfDay() / FixedSimulation.TICKS_PER_SECOND);
        }
    }

    @Test
    void rejectsOutOfRangeTimeInsteadOfWrappingInvalidInput() {
        for (int invalid : new int[] {Integer.MIN_VALUE, -1, WorldTime.TICKS_PER_DAY, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new WorldTime(invalid, true));
        for (int invalid : new int[] {Integer.MIN_VALUE, -1, WorldTime.MINECRAFT_TICKS_PER_DAY, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> WorldTime.INITIAL.withMinecraftTime(invalid));
    }
}
