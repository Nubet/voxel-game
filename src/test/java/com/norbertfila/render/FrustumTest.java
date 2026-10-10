package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class FrustumTest {
    @Test
    void classifiesInsideOutsideAndIntersectingAabbs() {
        Frustum frustum = Frustum.from(new Matrix4f());

        assertTrue(frustum.intersects(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f));
        assertFalse(frustum.intersects(2f, 2f, 2f, 3f, 3f, 3f));
        assertTrue(frustum.intersects(0.5f, 0.5f, 0.5f, 2f, 2f, 2f));
    }

    @Test
    void classifiesChunkBoundsInWorldCoordinates() {
        Frustum frustum = Frustum.from(new Matrix4f().translation(0, 0, -16));

        assertTrue(frustum.intersects(new com.norbertfila.world.ChunkPos(0, 0, 0)));
        assertFalse(frustum.intersects(new com.norbertfila.world.ChunkPos(10, 0, 0)));
    }
}
