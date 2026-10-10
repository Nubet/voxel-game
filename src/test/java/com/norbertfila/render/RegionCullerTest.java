package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.streaming.RegionIndex;
import com.norbertfila.world.ChunkPos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class RegionCullerTest {
    @Test
    void matchesSimpleChunkCullingAndRejectsWholeRegions() {
        RegionIndex index = new RegionIndex(2);
        List<ChunkPos> chunks = List.of(new ChunkPos(0, 0, 0), new ChunkPos(1, 0, 0), new ChunkPos(4, 0, 0));
        chunks.forEach(index::add);

        RegionCuller.Result result = new RegionCuller().cull(Frustum.from(new Matrix4f()), index);

        assertEquals(
                new HashSet<>(RegionCuller.simpleVisible(Frustum.from(new Matrix4f()), index)),
                new HashSet<>(result.visible()));
        assertEquals(Set.of(new ChunkPos(0, 0, 0)), new HashSet<>(result.visible()));
        assertEquals(2, result.regionTests());
        assertEquals(2, result.chunkTests());
        assertEquals(1, result.rejectedRegions());
    }

    @Test
    void regionBoundsUseWorldChunkSize() {
        Frustum frustum = Frustum.from(new Matrix4f());

        assertTrue(frustum.intersects(new com.norbertfila.world.RegionPos(0, 0, 0).bounds(1)));
        assertFalse(frustum.intersects(new com.norbertfila.world.RegionPos(2, 0, 0).bounds(1)));
    }
}
