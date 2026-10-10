package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.ChunkPos;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class LodManagerTest {
    private static final List<LodLevel> LEVELS =
            List.of(new LodLevel(0, 8, 4), new LodLevel(9, 16, 8), new LodLevel(17, 32, 16));

    @Test
    void plansContiguousNonOverlappingRingsNearFirst() {
        LodManager manager = new LodManager(LEVELS, 64, 500);
        List<LodManager.Request> requests = manager.plan(new ChunkPos(0, 0, 0));

        assertEquals(new HashSet<>(requests).size(), requests.size());
        for (int i = 1; i < requests.size(); i++) {
            assertTrue(requests.get(i - 1).distanceSquared() <= requests.get(i).distanceSquared());
        }
        assertTrue(requests.stream().anyMatch(request -> request.level().sampleStep() == 4));
        assertTrue(requests.stream().anyMatch(request -> request.level().sampleStep() == 16));
    }

    @Test
    void limitsTileCountAndReportsDistanceInBlocksAndChunks() {
        LodManager manager = new LodManager(LEVELS, 64, 7);

        assertEquals(7, manager.plan(new ChunkPos(0, 0, 0)).size());
        assertEquals(32, manager.renderDistanceChunks());
        assertEquals(32 * 16, manager.renderDistanceBlocks());
        assertNotEquals(manager.plan(new ChunkPos(0, 0, 0)), manager.plan(new ChunkPos(8, 0, 8)));
    }
}
