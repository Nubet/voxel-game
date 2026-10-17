package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.lod.*;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class FrozenWaterTest {
    @Test
    void coldRiverHasIceOverWaterInChunksAndFarTerrainAndEditsCanBreakIt() {
        var terrain = new TerrainGenerator(0, TerrainConfig.defaults());
        int x = -3776, z = -1920;
        var summary = terrain.sample(x, z);
        assertEquals(BiomeType.RIVER, summary.biome());
        assertTrue(summary.frozenWater());
        int y = summary.waterLevel();
        assertEquals(BlockId.ICE, summary.materialAt(y));
        assertEquals(BlockId.WATER, summary.materialAt(y - 1));
        var chunk = terrain.generate(new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16)));
        assertEquals(BlockId.ICE, chunk.get(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16)));
        var far = new FarTerrainGenerator(terrain);
        int tx = Math.floorDiv(x, 64), tz = Math.floorDiv(z, 64);
        var overlay = new WorldChangeOverlay();
        var tile = far.generate(tx, tz, 1, overlay.edits(tx * 64, tz * 64, 64));
        int lx = Math.floorMod(x, 64), lz = Math.floorMod(z, 64);
        assertEquals(y, tile.heightAt(lx, lz));
        assertEquals(BlockId.ICE, tile.materialAt(lx, lz));
        assertEquals(FarTerrainTile.NO_WATER, tile.waterTopAt(lx, lz));
        overlay.set(new BlockPos(x, y, z), BlockId.AIR, 64);
        tile = far.generate(tx, tz, 1, overlay.edits(tx * 64, tz * 64, 64));
        assertEquals(summary.surfaceHeight(), tile.heightAt(lx, lz));
        assertEquals(y - 1, tile.waterTopAt(lx, lz));
    }

    @Test
    void freezingUsesWaterClimateRatherThanAquaticBiomeIdentity() {
        assertTrue(SnowRules.freezes(WaterKind.LAKE, -.6f));
        assertTrue(SnowRules.freezes(WaterKind.RIVER, -.6f));
        assertFalse(SnowRules.freezes(WaterKind.OCEAN, -.6f));
        assertFalse(SnowRules.freezes(WaterKind.NONE, -.6f));
        assertFalse(SnowRules.freezes(WaterKind.LAKE, -.3f));
        assertFalse(new TerrainGenerator(42, TerrainConfig.defaults())
                .sample(3360, -4096)
                .frozenWater());
    }
}
