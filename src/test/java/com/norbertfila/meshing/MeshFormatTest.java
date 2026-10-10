package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MeshFormatTest {
    @Test
    void derivesCountAndBoundsFromFloatBuffer() {
        MeshData mesh = new MeshData(new float[] {1, 2, 3, 1, 1, 1, -1, 0, 4, 1, 1, 1});

        assertEquals(2, mesh.format().vertexCount());
        assertEquals(-1, mesh.format().bounds().minX());
        assertEquals(4, mesh.format().bounds().maxZ());
    }

    @Test
    void failsFastForMismatchedFormatBuffer() {
        MeshFormat format =
                new MeshFormat(1, 3, 3, new MeshFormat.Bounds(0, 0, 0, 1, 1, 1), MeshFormat.Topology.TRIANGLES);

        assertThrows(IllegalArgumentException.class, () -> new MeshData(format, new float[6]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeshFormat(1, 6, 2, new MeshFormat.Bounds(0, 0, 0, 1, 1, 1), MeshFormat.Topology.TRIANGLES));
    }
}
