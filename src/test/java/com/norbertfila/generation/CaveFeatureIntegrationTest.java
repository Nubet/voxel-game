package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class CaveFeatureIntegrationTest {
    @Test
    void naturalLavaAndWaterfallsSurviveParallelGenerationWithIdenticalFluidMetadata() throws Exception {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        var features = new HashSet<CaveFeatures.Feature>();
        for (int z = -16; z < 24; z++) for (int x = -16; x < 24; x++) features.addAll(terrain.caveFeatures(x, z));
        assertTrue(features.stream().anyMatch(f -> f.material() == BlockId.LAVA));
        assertTrue(features.stream().anyMatch(f -> f.type() == CaveFeatures.Type.SPRING));
        for (var feature : features)
            if (feature.type() == CaveFeatures.Type.SPRING) {
                var source = new BlockPos(feature.x(), feature.y(), feature.z());
                var local = Coordinates.worldToLocal(source);
                assertEquals(
                        BlockId.WATER,
                        terrain.generate(Coordinates.worldToChunk(source)).get(local.x(), local.y(), local.z()));
            }
        var positions = new HashSet<ChunkPos>();
        for (var feature : features)
            for (int z = Math.floorDiv(feature.z() - feature.radius(), 16);
                    z <= Math.floorDiv(feature.z() + feature.radius(), 16);
                    z++)
                for (int x = Math.floorDiv(feature.x() - feature.radius(), 16);
                        x <= Math.floorDiv(feature.x() + feature.radius(), 16);
                        x++)
                    for (int y = Math.floorDiv(feature.minY(), 16); y <= Math.floorDiv(feature.maxY(), 16); y++)
                        positions.add(new ChunkPos(x, y, z));
        var ordered = new ArrayList<>(positions);
        var expected = ordered.stream().map(p -> terrain.generate(p).toChunk()).toList();
        var parallel = new TerrainGenerator(42, TerrainConfig.defaults());
        int lava = 0, falling = 0;
        try (var workers = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<Chunk>>();
            for (var position : ordered.reversed())
                futures.add(workers.submit(() -> parallel.generate(position).toChunk()));
            for (int i = 0; i < futures.size(); i++) {
                var a = expected.get(expected.size() - 1 - i);
                var b = futures.get(i).get();
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) {
                            assertEquals(a.get(x, y, z), b.get(x, y, z));
                            assertEquals(a.waterState(x, y, z), b.waterState(x, y, z));
                            if (b.get(x, y, z) == BlockId.LAVA) {
                                lava++;
                                int wy = ordered.get(ordered.size() - 1 - i).y() * 16 + y;
                                assertTrue(wy >= -60 && wy <= -40);
                            }
                            if (b.waterState(x, y, z) == WaterState.FALLING) falling++;
                        }
            }
        }
        assertTrue(lava > 0 && falling > 0);
    }
}
