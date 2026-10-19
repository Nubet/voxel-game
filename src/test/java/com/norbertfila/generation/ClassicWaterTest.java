package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class ClassicWaterTest {
    @Test
    void classicCarvingPreservesElevatedRiverAndLakeBedsAndDryBanks() {
        var features = new ClassicTerrainFeatures(123, 28, (x, z) -> 64, (x, z) -> false);
        for (var kind : new WaterKind[] {WaterKind.RIVER, WaterKind.LAKE}) {
            for (int height : new int[] {64, 70}) {
                var base = new TerrainSummary(
                        height,
                        BlockId.GRASS,
                        BlockId.DIRT,
                        BlockId.STONE,
                        WorldBounds.MIN_Y,
                        67,
                        BiomeType.valueOf(kind.name()),
                        new ClimateSample(0, 0),
                        kind);
                var carved = features.apply(-1738, 4859, base, 1);
                assertNotNull(carved.shape());
                int supported = Math.min(height, base.waterLevel() + 1);
                for (int y = carved.shape().minY(); y <= supported; y++)
                    assertTrue(
                            BlockRegistry.DEFAULT.isCollidable(carved.materialAt(y)), "Carved away bed/bank at " + y);
                if (base.flooded()) assertEquals(BlockId.WATER, carved.materialAt(67));
            }
        }
    }

    @Test
    void reportedSeed123ColumnDoesNotBecomeAThirtySixBlockWaterSourceTower() {
        var terrain = new TerrainGenerator(123, TerrainConfig.defaults());
        var sample = terrain.sample(-1738, 4859);
        assertEquals(67, sample.waterLevel());
        assertTrue(BlockRegistry.DEFAULT.isCollidable(sample.materialAt(60)));
        var position = new ChunkPos(-109, 3, 303);
        var data = terrain.generate(position);
        assertTrue(BlockRegistry.DEFAULT.isCollidable(data.toChunk().get(6, 12, 11)));
        assertTrue(sample.waterLevel() - sample.surfaceHeight() <= 5);
        var river = terrain.sample(-1736, 4864);
        assertTrue(river.flooded());
        assertEquals(BlockId.WATER, river.materialAt(river.waterLevel()));
        assertTrue(river.waterLevel() - river.surfaceHeight() <= 5);
    }
}
