package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.simulation.WorldTime;
import org.junit.jupiter.api.Test;

class SkyModelTest {
    @Test
    void sunRisesInEastAndSetsInWestWithDayAndNightLighting() {
        double[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int i = 0; i < directions.length; i++) {
            var sky = SkyModel.at(i * WorldTime.TICKS_PER_DAY / 4d);
            assertEquals(directions[i][0], sky.sunX(), 1e-6);
            assertEquals(directions[i][1], sky.sunY(), 1e-6);
        }
        assertEquals(1, SkyModel.DAY.light());
        var night = SkyModel.at(WorldTime.TICKS_PER_DAY * .75);
        assertEquals(.16f, night.light());
        assertEquals(1, night.stars());
        assertEquals(0, SkyModel.DAY.stars());
        assertTrue(night.zenith().blue() < SkyModel.DAY.zenith().blue() * .1);
    }

    @Test
    void fullCycleHasFiniteBoundedColorsLightAndUnitDirections() {
        for (int tick = 0; tick < WorldTime.TICKS_PER_DAY; tick++) {
            var sky = SkyModel.at(tick);
            assertEquals(1, sky.sunX() * sky.sunX() + sky.sunY() * sky.sunY(), 2e-6);
            assertTrue(sky.light() >= .16f && sky.light() <= 1);
            assertTrue(sky.stars() >= 0 && sky.stars() <= 1);
            assertTrue(sky.twilight() >= 0 && sky.twilight() <= 1);
            for (var color : new SkyState.Color[] {sky.zenith(), sky.horizon(), sky.clouds()}) {
                assertTrue(color.red() >= 0 && color.red() <= 1);
                assertTrue(color.green() >= 0 && color.green() <= 1);
                assertTrue(color.blue() >= 0 && color.blue() <= 1);
            }
        }
    }

    @Test
    void interpolationCrossesMidnightBoundaryByOneTickAndDoesNotExtrapolate() {
        var previous = new WorldTime(WorldTime.TICKS_PER_DAY - 1, true);
        assertEquals(SkyModel.at(WorldTime.TICKS_PER_DAY - .5), SkyModel.interpolate(previous, WorldTime.INITIAL, .5));
        assertEquals(SkyModel.at(0), SkyModel.interpolate(previous, WorldTime.INITIAL, 1));
        var before = SkyModel.at(WorldTime.TICKS_PER_DAY - .001);
        var after = SkyModel.at(.001);
        assertEquals(before.horizon().red(), after.horizon().red(), 1e-5);
        assertEquals(before.light(), after.light(), 1e-5);
        assertEquals(before.sunY(), after.sunY(), 1e-5);
    }

    @Test
    void pausedFrozenAndCommandResetSamplesStayAtTheirPublishedTime() {
        for (boolean enabled : new boolean[] {true, false}) {
            var time = new WorldTime(54000, enabled);
            for (double alpha : new double[] {0, .25, .5, 1})
                assertEquals(SkyModel.at(54000), SkyModel.interpolate(time, time, alpha));
        }
    }

    @Test
    void rejectsInvalidTimeAndInterpolation() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> SkyModel.at(value));
        for (double alpha : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -.1, 1.1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkyModel.interpolate(WorldTime.INITIAL, WorldTime.INITIAL, alpha));
    }
}
