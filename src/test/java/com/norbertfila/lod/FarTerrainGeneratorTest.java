package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.world.ChunkPos;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FarTerrainGeneratorTest {
    @Test
    void generatesReducedHeightSamplesFromTheTerrainSource() {
        HillsGenerator source = new HillsGenerator(42L);
        FarTerrainGenerator generator = new FarTerrainGenerator(source, 64, 4, 0, 64);

        FarTerrainTile tile = generator.generate(-1, 2);

        assertEquals(-64, tile.originX());
        assertEquals(128, tile.originZ());
        assertEquals(17, tile.sampleCount());
        assertEquals(512, tile.triangleCount());
        assertEquals(source.sample(-64, 128).surfaceHeight(), tile.heightAt(0, 0));
        assertEquals(source.sample(-64, 128).surfaceMaterial(), tile.materialAt(0, 0));
        assertEquals(17 * 17 * 5L, tile.estimatedBytes());
    }

    @Test
    void appliesNearAndFarRadiusWithoutGeneratingOutsideRange() {
        FarTerrainGenerator generator = new FarTerrainGenerator(new HillsGenerator(), 64, 8, 3, 8);
        ChunkPos camera = new ChunkPos(0, 0, 0);

        Optional<FarTerrainTile> near = generator.generate(camera, 0, 0);
        Optional<FarTerrainTile> far = generator.generate(camera, 1, 0);
        Optional<FarTerrainTile> beyond = generator.generate(camera, 8, 0);

        assertFalse(near.isPresent());
        assertTrue(far.isPresent());
        assertFalse(beyond.isPresent());
    }
}
