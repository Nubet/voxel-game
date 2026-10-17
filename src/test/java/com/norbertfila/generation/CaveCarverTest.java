package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaveCarverTest {
    private final CaveCarver carver = new CaveCarver(new HillsGenerator(0));

    @Test
    void chunkedCarvingMatchesAnIndependentEllipsoidOracleAcrossAllAxes() {
        var segments =
                List.of(new CavePlan.Segment(-.25, -.5, .25, 5, 3), new CavePlan.Segment(15.8, 15.9, 16.2, 4, 6));
        var plan = new CavePlan(segments);
        for (int cy = -1; cy <= 1; cy++)
            for (int cz = -1; cz <= 1; cz++)
                for (int cx = -1; cx <= 1; cx++) {
                    byte[] blocks = stone();
                    carver.carve(new ChunkPos(cx, cy, cz), blocks, plan.segments(cx, cz));
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++) {
                                double wx = cx * 16 + x + .5, wy = cy * 16 + y + .5, wz = cz * 16 + z + .5;
                                boolean inside = segments.stream()
                                        .anyMatch(s -> Math.pow((wx - s.x()) / s.radiusXZ(), 2)
                                                        + Math.pow((wy - s.y()) / s.radiusY(), 2)
                                                        + Math.pow((wz - s.z()) / s.radiusXZ(), 2)
                                                < 1);
                                assertEquals(inside ? BlockId.AIR : BlockId.STONE, blocks[(y * 16 + z) * 16 + x]);
                            }
                }
    }

    @Test
    void onlyNaturalCarveableMaterialsAreReplaced() {
        byte[] blocks = stone();
        byte[] materials = {
            BlockId.BEDROCK, BlockId.WATER, BlockId.SAND, BlockId.LOG, BlockId.COBBLE, BlockId.DIRT, BlockId.GRASS
        };
        System.arraycopy(materials, 0, blocks, 0, materials.length);
        assertTrue(carver.carve(new ChunkPos(0, 0, 0), blocks, List.of(new CavePlan.Segment(4, 2, 2, 8, 8))));
        for (int i = 0; i < materials.length; i++)
            assertEquals(CaveCarver.carveable(materials[i]) ? BlockId.AIR : materials[i], blocks[i]);
        assertFalse(carver.carve(new ChunkPos(0, 0, 0), blocks, List.of(new CavePlan.Segment(100, 100, 100, 1, 1))));
    }

    @Test
    void cavesCutThroughEveryBadlandsLayer() {
        for (byte material : new byte[] {
            BlockId.RED_SAND, BlockId.TERRACOTTA, BlockId.TERRACOTTA_ORANGE,
            BlockId.TERRACOTTA_RED, BlockId.TERRACOTTA_YELLOW, BlockId.TERRACOTTA_BROWN,
            BlockId.TERRACOTTA_WHITE, BlockId.TERRACOTTA_LIGHT_GRAY
        }) {
            byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
            Arrays.fill(blocks, material);
            assertTrue(carver.carve(new ChunkPos(0, 0, 0), blocks, List.of(new CavePlan.Segment(8, 8, 8, 3, 3))));
            assertEquals(BlockId.AIR, blocks[(8 * 16 + 8) * 16 + 8]);
            assertEquals(material, blocks[0]);
        }
    }

    @Test
    void plansOwnTheirIndexAndRejectInvalidGeometry() {
        var input = new ArrayList<>(List.of(new CavePlan.Segment(0, 0, 0, 2, 2)));
        var plan = new CavePlan(input);
        input.clear();
        assertEquals(1, plan.segments(-1, -1).size());
        assertThrows(
                UnsupportedOperationException.class, () -> plan.segments(0, 0).clear());
        assertThrows(IllegalArgumentException.class, () -> new CavePlan.Segment(0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CavePlan.Segment(Double.NaN, 0, 0, 1, 1));
    }

    private static byte[] stone() {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        Arrays.fill(blocks, BlockId.STONE);
        return blocks;
    }
}
