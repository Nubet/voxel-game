package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.world.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LandscapeMaterialTest {
    @ParameterizedTest
    @CsvSource({"0,-48,-144,26", "42,-272,240,220", "-7,-512,512,26", "1234,-48,-144,44"})
    void landscapeTuningPreservesReferenceGroundHeights(long seed, int x, int z, int height) {
        TerrainGenerator generator = new TerrainGenerator(seed, TerrainConfig.defaults());
        assertEquals(height, generator.surfaceHeight(x, z));
        assertEquals(height, generator.sample(x, z).surfaceHeight());
    }

    @ParameterizedTest
    @CsvSource({
        "29.3,.5,0,SAND",
        "29.3,-.5,0,GRASS",
        "30,-.5,0,GRASS",
        "58,.5,1.5,GRASS",
        "58,-.5,1.5,STONE",
        "80,0,0,STONE",
        "102,.5,0,STONE",
        "102,-.5,0,SNOW",
        "110,0,1.25,SNOW",
        "110,0,1.3,STONE",
        "110,0,2,STONE"
    })
    void existingNoiseAndSlopeVaryShoreRockAndSnowTransitions(
            double elevation, double biome, double slope, String material) {
        byte expected = switch (material) {
            case "SAND" -> BlockId.SAND;
            case "SNOW" -> BlockId.SNOW;
            case "STONE" -> BlockId.STONE;
            default -> BlockId.GRASS;
        };
        TerrainConfig.Layer continental = new TerrainConfig.Layer(1, 1, .5, 2, 16);
        TerrainConfig.Layer hills = new TerrainConfig.Layer(2, 1, .5, 2, 128);
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(1, 1, .5, 2, 0);
        TerrainConfig base = new TerrainConfig(32, continental, hills, disabled);
        TerrainConfig config = new TerrainConfig(32, continental, hills, disabled, base.mountains(), base.warp(), 28);
        TerrainGenerator generator = new TerrainGenerator(
                42,
                config,
                (x, z) -> x > -24
                        ? Math.clamp(biome + slope * (x + 16) / 16, -1, 1)
                        : (elevation - 32 - biome * 16) / 128,
                (x, z) -> -1);
        TerrainSummary summary = generator.sample(-16, -16);
        int height = (int) Math.floor(elevation);
        assertEquals(height, summary.surfaceHeight());
        assertEquals(expected, summary.surfaceMaterial());
        byte subsurface =
                expected == BlockId.SNOW ? BlockId.STONE : expected == BlockId.GRASS ? BlockId.DIRT : expected;
        assertEquals(subsurface, summary.materialAt(height - 3));
        assertEquals(BlockId.STONE, summary.materialAt(height - 4));
        BlockPos position = new BlockPos(-16, height, -16);
        BlockPos local = Coordinates.worldToLocal(position);
        assertEquals(
                expected, generator.generate(Coordinates.worldToChunk(position)).get(local.x(), local.y(), local.z()));
        var tile = new FarTerrainGenerator(generator, 16, 4, 0, 64).generate(-1, -1);
        assertEquals(height, tile.heightAt(0, 0));
        assertEquals(expected, tile.materialAt(0, 0));
    }
}
