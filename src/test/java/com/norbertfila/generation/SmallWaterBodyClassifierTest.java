package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SmallWaterBodyClassifierTest {
    @Test
    void enclosedPoolsCrossChunkBoundariesWithoutBecomingOceanAndCacheTheWholePool() {
        var samples = new AtomicInteger();
        var classifier = new SmallWaterBodyClassifier((x, z) -> {
            samples.incrementAndGet();
            return x >= -18 && x <= 2 && z >= -18 && z <= 2;
        });
        assertEquals(WaterKind.LAKE, classifier.classify(-16, -16));
        int firstPass = samples.get();
        for (int z = -18; z <= 2; z++)
            for (int x = -18; x <= 2; x++) assertEquals(WaterKind.LAKE, classifier.classify(x, z));
        assertEquals(firstPass, samples.get(), "Connected water must not be searched again for every column");
    }

    @Test
    void narrowConnectionToOpenWaterIsNotMistakenForAnEnclosedPool() {
        var classifier = new SmallWaterBodyClassifier(
                (x, z) -> Math.abs((long) x) <= 3 && Math.abs((long) z) <= 3 || z == 0 && x >= 0 || x >= 32);
        assertEquals(WaterKind.OCEAN, classifier.classify(0, 0));
        assertEquals(WaterKind.OCEAN, classifier.classify(32, 0));
    }

    @Test
    void sizeLimitAndWorkerOrderDoNotChangeClassification() {
        var classifier = new SmallWaterBodyClassifier((x, z) -> x >= -32 && x < 0 && z >= -32 && z < 0 || x >= 32);
        IntStream.range(0, 64).parallel().forEach(i -> {
            assertEquals(WaterKind.LAKE, classifier.classify(-1 - i % 32, -1 - i / 32));
            assertEquals(WaterKind.OCEAN, classifier.classify(32 + i % 8, i / 8));
        });
        var larger = new SmallWaterBodyClassifier((x, z) -> x >= 0 && x < 33 && z >= 0 && z < 32);
        assertEquals(WaterKind.OCEAN, larger.classify(0, 0));
    }
}
