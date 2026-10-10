package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MeshDataTest {
    @Test
    void emptyMeshHasNoVertices() {
        MeshData mesh = new MeshData(new float[0]);

        assertEquals(0, mesh.vertexCount());
        assertEquals(0, mesh.vertices().length);
    }

    @Test
    void validatesStrideAndRemainsImmutable() {
        float[] source = {1, 2, 3, 4, 5, 6};
        MeshData mesh = new MeshData(source);
        source[0] = 99;
        float[] returned = mesh.vertices();
        returned[1] = 88;

        assertEquals(1, mesh.vertexCount());
        assertArrayEquals(new float[] {1, 2, 3, 4, 5, 6}, mesh.vertices());
        assertThrows(IllegalArgumentException.class, () -> new MeshData(new float[5]));
    }
}
