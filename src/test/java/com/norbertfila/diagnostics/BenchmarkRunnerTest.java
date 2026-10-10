package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.VisibleFaceMesher;
import com.norbertfila.world.ChunkPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkRunnerTest {
    @Test
    void reportsDifferentReferenceSceneGeometry() {
        BenchmarkRunner runner = new BenchmarkRunner(1, 2, List.of(new ChunkPos(0, 0, 0)));

        BenchmarkRunner.Result empty = runner.run(ScenePreset.EMPTY, new VisibleFaceMesher(), 7L);
        BenchmarkRunner.Result solid = runner.run(ScenePreset.SOLID, new VisibleFaceMesher(), 7L);

        assertEquals(0, empty.emittedFaces());
        assertEquals(6 * 16 * 16 * 2, solid.emittedFaces());
        assertTrue(solid.generateNanos() >= 0);
        assertTrue(solid.meshNanos() >= 0);
    }

    @Test
    void exportsCsvAndJsonReports() {
        BenchmarkRunner.Result result = new BenchmarkRunner.Result(ScenePreset.FLAT, 3L, 1, 2, 1, 4, 5L, 6L, 7L);

        assertTrue(BenchmarkRunner.toCsv(List.of(result)).contains("preset,seed"));
        assertTrue(BenchmarkRunner.toJson(List.of(result)).contains("\"emittedFaces\":7"));
    }
}
