package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BenchmarkSceneTest {
    private static final BenchmarkScene.Site SITE = new BenchmarkScene.Site(-37, 21, 48, 90);

    @ParameterizedTest
    @EnumSource(BenchmarkScene.class)
    void commandsDoNotDependOnFrameSlicing(BenchmarkScene scene) {
        double end = scene.setupSeconds() + scene.measureSeconds();
        assertEquals(commands(scene, end, 1.0 / 60), commands(scene, end, 1.0 / 997));
        assertEquals(commands(scene, end, 1.0 / 60), commands(scene, end, end));
    }

    @ParameterizedTest
    @EnumSource(BenchmarkScene.class)
    void posesAreDeterministic(BenchmarkScene scene) {
        for (double t = 0; t < scene.setupSeconds() + scene.measureSeconds(); t += .37)
            assertEquals(scene.pose(t, SITE), scene.pose(t, SITE));
    }

    @Test
    void returnFlightRetracesTheNewTerrainFlight() {
        double seconds = BenchmarkScene.FLIGHT_SECONDS;
        assertEquals(
                BenchmarkScene.FLY_NEW.pose(seconds, SITE).x(),
                BenchmarkScene.FLY_RETURN.pose(0, SITE).x(),
                1e-9);
        assertEquals(SITE.x(), BenchmarkScene.FLY_RETURN.pose(seconds, SITE).x(), 1e-9);
    }

    @Test
    void editsTargetChunkBorders() {
        List<WorldRuntime.Action> actions = new ArrayList<>();
        BenchmarkScene.EDITS.commands(0, .4, SITE, actions::add);
        assertEquals(4, actions.size());
        for (var action : actions) {
            var fill = (WorldRuntime.Fill) action;
            assertEquals(14, Math.floorMod(fill.min().x(), 16));
            assertEquals(1, Math.floorMod(fill.max().x(), 16));
        }
    }

    private static List<WorldRuntime.Action> commands(BenchmarkScene scene, double end, double step) {
        List<WorldRuntime.Action> actions = new ArrayList<>();
        double previous = 0;
        for (double t = step; previous < end; t += step) {
            double next = Math.min(t, end);
            scene.commands(previous, next, SITE, actions::add);
            previous = next;
        }
        return actions;
    }
}
