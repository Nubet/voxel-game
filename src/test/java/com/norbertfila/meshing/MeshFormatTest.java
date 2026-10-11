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

    @Test
    void texturedFormatUsesVersionedSevenFloatStrideAndPositionOnlyBounds() {
        MeshData mesh = MeshData.textured(new float[] {
            1, 2, 3, 4, 5, 6, 7,
            -1, 0, 4, 100, 100, 100, 100,
            0, -2, 1, 200, 200, 200, 200
        });
        assertEquals(MeshFormat.TEXTURED_VERSION, mesh.format().version());
        assertEquals(MeshFormat.TEXTURED_VERTEX_STRIDE, mesh.format().stride());
        assertEquals(3, mesh.vertexCount());
        assertEquals(-1, mesh.format().bounds().minX());
        assertEquals(-2, mesh.format().bounds().minY());
        assertEquals(1, mesh.format().bounds().minZ());
        assertEquals(1, mesh.format().bounds().maxX());
        assertEquals(2, mesh.format().bounds().maxY());
        assertEquals(4, mesh.format().bounds().maxZ());
        assertThrows(IllegalArgumentException.class, () -> MeshData.textured(new float[8]));
    }
}
