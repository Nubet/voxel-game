package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainRenderOrderTest {
    @Test
    void sortsBackToFrontUsingAllThreeAxes() {
        ChunkPos near = new ChunkPos(0, 0, 0);
        ChunkPos middle = new ChunkPos(0, 2, 0);
        ChunkPos far = new ChunkPos(0, 0, -4);
        var positions = new ArrayList<>(List.of(middle, near, far));
        positions.sort(TerrainRenderer.backToFront(8, 8, 8));
        assertEquals(List.of(far, middle, near), positions);
        positions.sort(TerrainRenderer.backToFront(8, 8, -56));
        assertEquals(List.of(middle, near, far), positions);
    }

    @Test
    void usesChunkCentersAtNegativeCoordinates() {
        ChunkPos near = new ChunkPos(-2, -1, -3);
        ChunkPos far = new ChunkPos(-5, -1, -3);
        var positions = new ArrayList<>(List.of(near, far));
        positions.sort(TerrainRenderer.backToFront(-24, -8, -40));
        assertEquals(List.of(far, near), positions);
    }

    @Test
    void equalDistancesHaveDeterministicCoordinateOrder() {
        ChunkPos left = new ChunkPos(-1, 0, 0);
        ChunkPos right = new ChunkPos(1, 0, 0);
        var positions = new ArrayList<>(List.of(right, left));
        positions.sort(TerrainRenderer.backToFront(8, 8, 8));
        assertEquals(List.of(left, right), positions);
    }

    @Test
    void distanceDoesNotOverflowIntegralWorldCoordinates() {
        ChunkPos near = new ChunkPos(Integer.MAX_VALUE, 0, 0);
        ChunkPos far = new ChunkPos(Integer.MIN_VALUE, 0, 0);
        var positions = new ArrayList<>(List.of(near, far));
        positions.sort(TerrainRenderer.backToFront((double) Integer.MAX_VALUE * Chunk.SIZE + 8, 8, 8));
        assertEquals(List.of(far, near), positions);
    }

    @Test
    void rejectsNonFiniteCameraPositions() {
        assertThrows(IllegalArgumentException.class, () -> TerrainRenderer.backToFront(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TerrainRenderer.backToFront(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> TerrainRenderer.backToFront(0, 0, Double.NEGATIVE_INFINITY));
    }
}
