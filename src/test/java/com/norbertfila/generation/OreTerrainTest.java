package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OreTerrainTest {
    @Test
    void oresDecorateUniformStoneAndPreserveCavesAndBedrock() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        var ores = new OreGenerator(42);
        var seen = new java.util.HashSet<Byte>();
        int exposed = 0;
        for (int cz = -4; cz <= 3; cz++)
            for (int cx = -4; cx <= 3; cx++)
                for (int cy = -4; cy <= 1; cy++) {
                    var position = new ChunkPos(cx, cy, cz);
                    byte[] candidates = new byte[ChunkData.BLOCK_COUNT];
                    Arrays.fill(candidates, BlockId.STONE);
                    ores.generate(position, candidates);
                    var data = terrain.generate(position);
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++) {
                                byte actual = data.get(x, y, z);
                                byte candidate = candidates[(y * 16 + z) * 16 + x];
                                if (cy == -4 && y == 0) assertEquals(BlockId.BEDROCK, actual);
                                if (candidate == BlockId.STONE) continue;
                                if (actual == candidate) seen.add(actual);
                                else if (actual == BlockId.AIR) exposed++;
                                else assertNotEquals(BlockId.STONE, actual, "Ore pass was skipped");
                            }
                }
        assertEquals(5, seen.size());
        assertTrue(exposed > 0, "Caves must remain empty even where a vein intersects them");
    }
}
