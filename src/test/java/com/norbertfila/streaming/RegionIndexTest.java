package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RegionPos;
import org.junit.jupiter.api.Test;

class RegionIndexTest {
    @Test
    void createsAndRemovesRegionsWithoutStoringChunkData() {
        RegionIndex index = new RegionIndex(8);
        ChunkPos first = new ChunkPos(-1, 0, 0);
        ChunkPos second = new ChunkPos(0, 0, 0);

        assertTrue(index.add(first));
        assertFalse(index.add(first));
        assertTrue(index.add(second));
        assertEquals(2, index.size());
        assertEquals(2, index.regions().size());
        assertTrue(index.contains(first));

        assertTrue(index.remove(first));
        assertFalse(index.contains(first));
        assertEquals(1, index.regions().size());
        assertFalse(index.remove(first));
        assertEquals(new RegionPos(0, 0, 0), index.regionOf(second));
    }
}
