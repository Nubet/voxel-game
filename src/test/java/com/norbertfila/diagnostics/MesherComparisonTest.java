package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.ChunkPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class MesherComparisonTest {
    @Test
    void comparesTheSameScenesWithBothMeshers() {
        List<MesherComparison.Result> results = MesherComparison.run(0, 1, List.of(new ChunkPos(0, 0, 0)), 7L);

        assertEquals(ScenePreset.values().length, results.size());
        assertTrue(results.stream().allMatch(MesherComparison.Result::equivalent));
        MesherComparison.Result flat = results.stream()
                .filter(result -> result.preset() == ScenePreset.FLAT)
                .findFirst()
                .orElseThrow();
        assertTrue(flat.greedyVertices() < flat.baselineVertices());
        assertTrue(flat.greedyBytes() < flat.baselineBytes());
    }

    @Test
    void exportsBenchmarkColumns() {
        List<MesherComparison.Result> results = MesherComparison.run(0, 1, List.of(new ChunkPos(0, 0, 0)), 7L);

        assertTrue(MesherComparison.toCsv(results).contains("baselineSingleRemeshNanos,baselineBatchRemeshNanos"));
    }
}
