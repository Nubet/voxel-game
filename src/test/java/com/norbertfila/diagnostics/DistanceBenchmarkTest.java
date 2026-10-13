package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DistanceBenchmarkTest {
    @Test
    void producesThreeComparableRunsAtEveryDistance() {
        List<DistanceBenchmark.Result> results = DistanceBenchmark.run(0, 1, 3, 7L);

        assertEquals(12, results.size());
        assertTrue(results.stream().allMatch(result -> result.seed() == 7L));
        assertTrue(results.stream().allMatch(result -> result.p99FrameMillis() >= 0.0));
        assertTrue(results.stream().allMatch(result -> result.nearChunks() > 0));
        assertTrue(DistanceBenchmark.toCsv(results).contains("streamingSpikeMillis"));
        String json = DistanceBenchmark.toJson(results);
        assertTrue(json.startsWith("[") && json.endsWith("]"));
    }
}
