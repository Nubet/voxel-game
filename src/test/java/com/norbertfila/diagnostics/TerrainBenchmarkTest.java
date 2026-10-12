package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.world.ChunkPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainBenchmarkTest {
    @Test
    void countsActualOctavesIncludingMaskEarlyOut() {
        TerrainConfig config = TerrainConfig.classic();
        List<ChunkPos> positions = List.of(new ChunkPos(0, 2, 0));
        TerrainConfig plains =
                new TerrainConfig(config.baseHeight(), config.continental(), config.hills(), config.details());
        assertEquals(2431, TerrainBenchmark.countNoiseSamples(0, plains, positions));
        assertEquals(2963, TerrainBenchmark.countNoiseSamples(0, config, positions));
    }

    @Test
    void reportsSeparateHeightGenerationAndSnapshotWorkloads() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        var result = TerrainBenchmark.run("mountains", generator, List.of(new ChunkPos(-1, 2, -1)), 2, 2, 2816);
        assertEquals(42, result.seed());
        assertEquals(generator.generatorVersion(), result.version());
        assertEquals(1, result.chunks());
        assertEquals(2, result.iterations());
        assertTrue(result.heights().nanos() > 0);
        assertTrue(result.generate().nanos() > 0);
        assertTrue(result.snapshot().nanos() > 0);
        assertTrue(result.heights().bytes() >= -1);
        assertTrue(result.generate().bytes() == -1 || result.generate().bytes() >= 8192);
        assertTrue(result.snapshot().bytes() == -1
                || result.snapshot().bytes() > result.generate().bytes());
        String csv = TerrainBenchmark.toCsv(List.of(result));
        assertTrue(csv.contains("heightBytesPerColumn"));
        assertTrue(csv.contains("mountains,42,10,1,2,2,"));
    }

    @Test
    void rejectsEmptyScenesAndInvalidIterationCounts() {
        TerrainGenerator generator = new TerrainGenerator(0, TerrainConfig.defaults());
        assertThrows(
                IllegalArgumentException.class, () -> TerrainBenchmark.run("terrain", generator, List.of(), 0, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> TerrainBenchmark.run("terrain", generator, List.of(new ChunkPos(0, 0, 0)), -1, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> TerrainBenchmark.run("terrain", generator, List.of(new ChunkPos(0, 0, 0)), 0, 0, 0));
    }
}
