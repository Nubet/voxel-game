package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JungleCanopyTest {
    private static TerrainSource scene(Canopy canopy) {
        return new TerrainSource() {
            public long seed() {
                return 42;
            }

            public int generatorVersion() {
                return 0;
            }

            public int surfaceHeight(int x, int z) {
                return 8;
            }

            public TerrainSummary sample(int x, int z) {
                return new TerrainSummary(8, BlockId.GRASS, BlockId.DIRT, BlockId.STONE);
            }

            public ChunkData generate(ChunkPos pos) {
                throw new AssertionError("Far canopy must not generate chunks");
            }

            public List<Canopy> canopies(int x, int z, int size) {
                return List.of(canopy);
            }
        };
    }

    @Test
    void crownsAreSeparateFromGroundClippedAndIncludedInBounds() {
        var source = scene(new Canopy(63, 40, 10, 5, 2, 42, BlockId.JUNGLE_LEAVES));
        var generator = new FarTerrainGenerator(source);
        for (int step : new int[] {1, 4, 8, 16}) {
            var tile = generator.generate(0, 0, step, AreaEdits.none(0, 0, 64));
            assertEquals(8, tile.heightAt(0, 0));
            assertTrue(tile.maxHeight() >= 42);
            assertTrue(tile.canopyQuads().length > 0);
            for (int i = 0; i < tile.canopyQuads().length; i += 3) {
                int position = tile.canopyQuads()[i];
                assertTrue((position & 127) <= 64);
                assertTrue(((position >>> 7) & 127) <= 64);
                assertTrue(tile.canopyQuads()[i + 1] >= 38);
            }
            var neighbor = generator.generate(1, 0, step, AreaEdits.none(64, 0, 64));
            assertTrue(neighbor.canopyQuads().length > 0);
        }
    }

    @Test
    void removedCrownDoesNotReappearInFarGeometry() {
        var canopy = new Canopy(12, 40, 12, 3, 2, 42, BlockId.JUNGLE_LEAVES);
        Map<Integer, List<Long>> changes = new HashMap<>();
        for (int z = 9; z <= 15; z++)
            for (int x = 9; x <= 15; x++)
                for (int y = 38; y <= 42; y++)
                    if (canopy.contains(x, y, z))
                        changes.computeIfAbsent(z * 64 + x, k -> new ArrayList<>())
                                .add((long) y << 32);
        var source = scene(canopy);
        assertTrue(FarCanopyMesher.mesh(source, 0, 0, 16, AreaEdits.none(0, 0, 64)).length > 0);
        assertEquals(0, FarCanopyMesher.mesh(source, 0, 0, 16, AreaEdits.of(0, 0, 64, 1, changes)).length);
    }
}
