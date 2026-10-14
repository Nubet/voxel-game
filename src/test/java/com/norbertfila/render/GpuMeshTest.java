package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.meshing.QuadRanges;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpuMeshTest {
    @Test
    void keepsLayerRangesAndSizeOfTheUploadedQuads() {
        QuadMesh quads = new QuadMesh(QuadMesh.Format.SOLID, new int[6 * 2], new QuadRanges(3, 2, 1));
        List<Integer> deleted = new ArrayList<>();
        GpuMesh mesh = new GpuMesh(7, quads, deleted::add);
        assertEquals(QuadMesh.Format.SOLID, mesh.format());
        assertEquals(new QuadRanges(3, 2, 1), mesh.ranges());
        assertEquals(48, mesh.bytes());
        assertEquals(7, mesh.buffer());
    }

    @Test
    void closeDeletesTheBufferOnlyOnce() {
        List<Integer> deleted = new ArrayList<>();
        GpuMesh mesh = new GpuMesh(7, QuadMesh.empty(QuadMesh.Format.WATER), deleted::add);

        mesh.close();
        mesh.close();

        assertTrue(mesh.closed());
        assertEquals(List.of(7), deleted);
    }
}
