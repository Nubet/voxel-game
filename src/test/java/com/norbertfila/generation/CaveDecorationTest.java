package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.PlantRules;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaveDecorationTest {
    @Test
    void vegetationCannotUseSurfaceRemovedByCavesEvenAcrossVerticalSections() {
        TerrainSummary[] summaries = new TerrainSummary[256];
        java.util.Arrays.fill(summaries, new TerrainSummary(15, BlockId.GRASS, BlockId.DIRT, BlockId.STONE));
        var removed = new BitSet(256);
        removed.set(0, 256);
        var column = TerrainColumn.of(summaries, List.of(), List.of(), removed);
        removed.clear();
        byte[] blocks = new byte[4096];
        new VegetationGenerator(42, (x, z) -> 1, (x, z) -> 1).generate(0, 16, 0, blocks, column);
        for (byte block : blocks) assertEquals(BlockId.AIR, block);
    }

    @Test
    void naturalEntrancesHaveNoFloatingPlantsOrTrunks() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        Map<ChunkPos, ChunkData> sections = new HashMap<>();
        int entrances = 0;
        for (int cz = -24; cz < 24; cz++)
            for (int cx = -24; cx < 24; cx++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        int wx = cx * 16 + x, wz = cz * 16 + z;
                        var summary = terrain.sample(wx, wz);
                        int y = summary.surfaceHeight();
                        if (at(terrain, sections, wx, y, wz) != BlockId.AIR) continue;
                        entrances++;
                        byte above = at(terrain, sections, wx, y + 1, wz);
                        assertFalse(PlantRules.isPlant(above));
                        assertNotEquals(BlockId.LOG, above);
                    }
        assertTrue(entrances > 0, "Dry surface openings must be reachable without mining");
    }

    private static byte at(TerrainGenerator terrain, Map<ChunkPos, ChunkData> sections, int x, int y, int z) {
        var position = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
        return sections.computeIfAbsent(position, terrain::generate)
                .get(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
    }
}
