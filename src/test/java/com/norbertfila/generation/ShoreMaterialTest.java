package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.world.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShoreMaterialTest {
    @ParameterizedTest
    @CsvSource({
        "32,-3,0,GRASS", "32,-2,0,GRASS", "30,-2,0,GRASS", "32,-1,0,SAND",
        "32,0,1.5,SAND", "32,0,1.51,STONE", "32,1,0,SAND", "32,3,0,SAND",
        "32,4,0,GRAVEL", "32,1,2,GRAVEL", "32,-1,2,STONE"
    })
    void selectsMaterialsByWaterDepthSlopeAndExistingNoise(int height, int depth, double slope, String material) {
        byte expected = switch (material) {
            case "SAND" -> BlockId.SAND;
            case "GRAVEL" -> BlockId.GRAVEL;
            case "STONE" -> BlockId.STONE;
            default -> BlockId.GRASS;
        };
        TerrainConfig.Layer enabled = new TerrainConfig.Layer(1, 1, .5, 2, 8);
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(1, 1, .5, 2, 0);
        TerrainConfig base = new TerrainConfig(32, enabled, disabled, disabled);
        TerrainConfig config =
                new TerrainConfig(32, enabled, disabled, disabled, base.mountains(), base.warp(), height + depth);
        for (int axis = 0; axis < 2; axis++) {
            int slopeAxis = axis;
            TerrainGenerator generator = new TerrainGenerator(
                    42,
                    config,
                    (x, z) -> Math.clamp((height - 32 + slope * (slopeAxis == 0 ? x + 16 : z + 16)) / 8, -1, 1),
                    (x, z) -> -1);
            TerrainSummary sample = generator.sample(-16, -16);
            assertEquals(height, sample.surfaceHeight());
            assertEquals(expected, sample.surfaceMaterial());
            byte subsurface = expected == BlockId.GRASS ? BlockId.DIRT : expected;
            for (int y = height - 3; y <= height; y++) {
                byte block = y == height ? expected : subsurface;
                assertEquals(block, sample.materialAt(y));
                BlockPos pos = new BlockPos(-16, y, -16);
                BlockPos local = Coordinates.worldToLocal(pos);
                assertEquals(
                        block, generator.generate(Coordinates.worldToChunk(pos)).get(local.x(), local.y(), local.z()));
            }
            assertEquals(BlockId.STONE, sample.materialAt(height - 4));
            assertEquals(depth > 0 ? BlockId.WATER : BlockId.AIR, sample.materialAt(height + 1));
            var tile = new FarTerrainGenerator(generator, 16, 4, 0, 64).generate(-1, -1);
            assertEquals(height, tile.heightAt(0, 0));
            assertEquals(expected, tile.materialAt(0, 0));
        }
    }
}
