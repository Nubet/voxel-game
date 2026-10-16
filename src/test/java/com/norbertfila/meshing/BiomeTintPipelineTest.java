package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.biome.*;
import com.norbertfila.lod.*;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class BiomeTintPipelineTest {
    @Test
    void tintBoundariesSurviveGreedyMeshingAndPublishedSnapshots() {
        var chunk = Chunk.uniform(BlockId.GRASS);
        byte[] tints = new byte[256];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) tints[z * 16 + x] = (byte) (x < 8 ? 15 : 23);
        chunk.setTints(tints);
        var snapshot = chunk.snapshot();
        tints[0] = 31;
        assertEquals(15, snapshot.tintIndex(0, 0));
        var world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        var neighborhood = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        var mesh = GreedyMesher.meshSolids(snapshot, neighborhood);
        int tops = 0;
        var indices = new java.util.HashSet<Integer>();
        int[] records = mesh.records();
        for (int i = 0; i < records.length; i += 2)
            if ((records[i] >>> 15 & 7) == 2) {
                tops++;
                assertEquals(BlockRegistry.GRASS_TOP_TEXTURE, records[i + 1] & 255);
                indices.add(records[i + 1] >>> 8 & 31);
            }
        assertEquals(2, tops);
        assertEquals(java.util.Set.of(15, 23), indices);
        chunk.setTints(new byte[256]);
        assertEquals(15, snapshot.tintIndex(0, 0));
        assertEquals(0, chunk.snapshot().tintIndex(0, 0));
    }

    @Test
    void nearAndFarTerrainUseIdenticalTintSamplesAtSignedBoundaries() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        for (int x : new int[] {-65, -64, -1, 0, 63, 64}) {
            int z = -17;
            var position = new ChunkPos(Math.floorDiv(x, 16), 2, Math.floorDiv(z, 16));
            var snapshot = terrain.generate(position).toChunk().snapshot();
            var far = new FarTerrainGenerator(terrain)
                    .generate(
                            Math.floorDiv(x, 64),
                            Math.floorDiv(z, 64),
                            1,
                            AreaEdits.none(Math.floorDiv(x, 64) * 64, Math.floorDiv(z, 64) * 64, 64));
            assertEquals(terrain.tintIndex(x, z), snapshot.tintIndex(Math.floorMod(x, 16), Math.floorMod(z, 16)));
            assertEquals(terrain.tintIndex(x, z), far.tintAt(Math.floorMod(x, 64), Math.floorMod(z, 64)));
        }
        int previous = 0;
        for (int x = 0; x <= 64; x++) {
            int index =
                    BiomeTint.blend(BiomeType.PLAINS, BiomeType.TAIGA, BiomeType.PLAINS, BiomeType.TAIGA, x / 64.0, 0);
            assertTrue(Math.abs(index % 8 - previous) <= 1);
            previous = index % 8;
        }
    }
}
