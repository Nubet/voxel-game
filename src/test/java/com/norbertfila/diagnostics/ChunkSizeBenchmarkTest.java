package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkSizeBenchmarkTest {
    @Test
    void reportsBothShapesForTheMixedBenchmark() {
        List<ChunkSizeBenchmark.Result> results = ChunkSizeBenchmark.run(32, 0, 1, 7L);

        assertEquals(8, results.size());
        assertEquals(16, results.get(0).shape().edge());
        assertEquals(32, results.get(4).shape().edge());
        assertTrue(results.stream().allMatch(result -> result.volumeBlocks() == 32L * 32 * 32));
        assertTrue(ChunkSizeBenchmark.toCsv(results).contains("staleRemeshNanos"));
    }
}
