package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GpuArenaBenchmarkTest {
    @Test
    void comparesTheSameFixedSceneForBothStrategies() {
        GpuArenaBenchmark.Result result = GpuArenaBenchmark.run();

        assertEquals("fixed-mesh-scene-v1", result.sceneId());
        assertEquals(7, result.meshCount());
        assertTrue(result.arenaCapacityBytes() >= result.perMeshBytes());
        assertTrue(result.arenaWasteBytes() >= 0);
        assertTrue(GpuArenaBenchmark.toJson(result).contains("gpuMeasured"));
    }
}
