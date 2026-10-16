package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.lod.AreaEdits;
import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalWaterTest {
    @Test
    void mixedLevelsDoNotBecomeUniformWaterOrAir() {
        TerrainSummary[] samples = new TerrainSummary[256];
        for (int i = 0; i < samples.length; i++) samples[i] = summary(-4, i % 16 < 8 ? 48 : 16, WaterKind.LAKE);
        var column = TerrainColumn.of(samples, List.of());
        assertEquals(BlockId.WATER, column.uniformBlock(0));
        assertEquals(-1, column.uniformBlock(16));
        assertEquals(-1, column.uniformBlock(32));
        assertEquals(BlockId.WATER, column.materialAt(0, 40));
        assertEquals(BlockId.AIR, column.materialAt(8, 40));
        assertEquals(BlockId.AIR, column.uniformBlock(64));
        samples[0] = summary(-4, TerrainSummary.NO_WATER, WaterKind.NONE);
        var withDryColumn = TerrainColumn.of(samples, List.of());
        assertEquals(-1, withDryColumn.uniformBlock(0));
        assertEquals(BlockId.AIR, withDryColumn.materialAt(0, 8));
        assertEquals(BlockId.WATER, withDryColumn.materialAt(1, 8));
    }

    @Test
    void farTerrainKeepsIndependentWaterLevels() {
        TerrainSource terrain = new TerrainSource() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public int surfaceHeight(int x, int z) {
                return 0;
            }

            public TerrainSummary sample(int x, int z) {
                return summary(0, x < 0 ? 48 : 16, x < 0 ? WaterKind.LAKE : WaterKind.RIVER);
            }

            public ChunkData generate(ChunkPos position) {
                throw new UnsupportedOperationException();
            }
        };
        var far = new FarTerrainGenerator(terrain);
        assertEquals(48, far.generate(-1, 0, 1, AreaEdits.none(-64, 0, 64)).waterTopAt(63, 0));
        assertEquals(16, far.generate(0, 0, 1, AreaEdits.none(0, 0, 64)).waterTopAt(0, 0));
    }

    @Test
    void cavesProtectRaisedLakeAcrossChunkBoundaryEvenWhenCurrentColumnHasNoWater() {
        TerrainSource terrain = new TerrainSource() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public int surfaceHeight(int x, int z) {
                return x >= 16 ? 32 : 64;
            }

            public TerrainSummary sample(int x, int z) {
                return summary(
                        surfaceHeight(x, z),
                        x >= 16 ? 48 : TerrainSummary.NO_WATER,
                        x >= 16 ? WaterKind.LAKE : WaterKind.NONE);
            }

            public ChunkData generate(ChunkPos position) {
                throw new UnsupportedOperationException();
            }
        };
        var caves = new CaveCarver(terrain);
        var segments = List.of(new CavePlan.Segment(15.5, 40.5, 8.5, 8, 8));
        assertFalse(caves.carves(15, 40, 8, BlockId.STONE, segments));
        assertTrue(caves.carves(14, 40, 8, BlockId.STONE, segments));
        assertFalse(caves.carves(16, 32, 8, BlockId.STONE, List.of(new CavePlan.Segment(16.5, 32.5, 8.5, 8, 8))));
    }

    @Test
    void dryColumnsCannotDeclareWaterKind() {
        assertThrows(IllegalArgumentException.class, () -> summary(0, TerrainSummary.NO_WATER, WaterKind.LAKE));
        assertThrows(IllegalArgumentException.class, () -> summary(0, 16, WaterKind.NONE));
    }

    private static TerrainSummary summary(int height, int level, WaterKind kind) {
        return new TerrainSummary(
                height,
                BlockId.GRASS,
                BlockId.DIRT,
                BlockId.STONE,
                TerrainSummary.NO_BEDROCK,
                level,
                BiomeType.PLAINS,
                new ClimateSample(0, 0),
                kind);
    }
}
