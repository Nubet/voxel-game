package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegionPosTest {
    @Test
    void roundTripsPositiveAndNegativeChunkCoordinates() {
        int edge = 8;
        for (int x = -33; x <= 33; x++)
            for (int y = -17; y <= 17; y++)
                for (int z = -9; z <= 9; z++) {
                    ChunkPos chunk = new ChunkPos(x, y, z);
                    RegionPos region = RegionPos.fromChunk(chunk, edge);
                    assertEquals(chunk, region.chunkAt(region.localChunk(chunk, edge), edge));
                }
    }

    @Test
    void negativeBoundaryUsesFloorDivision() {
        RegionPos region = RegionPos.fromChunk(new ChunkPos(-1, -8, -9), 8);

        assertEquals(new RegionPos(-1, -1, -2), region);
        assertEquals(new ChunkPos(7, 0, 7), region.localChunk(new ChunkPos(-1, -8, -9), 8));
        assertTrue(region.bounds(8).contains(new ChunkPos(-1, -8, -9)));
    }
}
