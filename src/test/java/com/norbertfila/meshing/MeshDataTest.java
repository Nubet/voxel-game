package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.world.RenderLayer;
import org.junit.jupiter.api.Test;

class MeshDataTest {
    @Test
    void emptyMeshHasNoVertices() {
        MeshData mesh = new MeshData(new float[0]);

        assertEquals(0, mesh.vertexCount());
        assertEquals(0, mesh.vertices().length);
        assertEquals(new MeshRanges(0, 0, 0), mesh.ranges());
    }

    @Test
    void validatesStrideAndRemainsImmutable() {
        float[] source = {1, 2, 3, 4, 5, 6};
        MeshData mesh = new MeshData(source);
        source[0] = 99;
        float[] returned = mesh.vertices();
        returned[1] = 88;

        assertEquals(1, mesh.vertexCount());
        assertEquals(new MeshRanges(1, 0, 0), mesh.ranges());
        assertArrayEquals(new float[] {1, 2, 3, 4, 5, 6}, mesh.vertices());
        assertThrows(IllegalArgumentException.class, () -> new MeshData(new float[5]));
    }

    @Test
    void layerCountsDefineContiguousRangesAndPreserveSnapshotOwnership() {
        float[] vertices = new float[18 * MeshFormat.TEXTURED_VERTEX_STRIDE];
        MeshRanges ranges = new MeshRanges(3, 6, 9);
        MeshData mesh = MeshData.textured(vertices, ranges);
        vertices[0] = 99;
        float[] copy = mesh.vertices();
        copy[1] = 88;
        assertArrayEquals(new float[vertices.length], mesh.vertices());
        assertEquals(ranges, mesh.ranges());
        assertEquals(0, ranges.firstVertex(RenderLayer.OPAQUE));
        assertEquals(3, ranges.firstVertex(RenderLayer.CUTOUT));
        assertEquals(9, ranges.firstVertex(RenderLayer.TRANSLUCENT));
        assertEquals(3, ranges.vertexCount(RenderLayer.OPAQUE));
        assertEquals(6, ranges.vertexCount(RenderLayer.CUTOUT));
        assertEquals(9, ranges.vertexCount(RenderLayer.TRANSLUCENT));
    }

    @Test
    void rejectsNegativeOverflowingIncompleteAndMisalignedRanges() {
        assertThrows(IllegalArgumentException.class, () -> new MeshRanges(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MeshRanges(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MeshRanges(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new MeshRanges(Integer.MAX_VALUE, 1, 0));
        float[] vertices = new float[18 * MeshFormat.TEXTURED_VERTEX_STRIDE];
        assertThrows(IllegalArgumentException.class, () -> MeshData.textured(vertices, new MeshRanges(6, 6, 3)));
        assertThrows(IllegalArgumentException.class, () -> MeshData.textured(vertices, new MeshRanges(6, 6, 9)));
        assertThrows(IllegalArgumentException.class, () -> MeshData.textured(vertices, new MeshRanges(4, 8, 6)));
        assertThrows(NullPointerException.class, () -> MeshData.textured(vertices, null));
    }

    @Test
    void rangeAlignmentUsesDeclaredTopology() {
        float[] vertices = new float[4 * MeshData.VERTEX_STRIDE];
        MeshFormat.Bounds bounds = new MeshFormat.Bounds(0, 0, 0, 0, 0, 0);
        MeshFormat points = new MeshFormat(1, MeshData.VERTEX_STRIDE, 4, bounds, MeshFormat.Topology.POINTS);
        assertEquals(new MeshRanges(1, 1, 2), new MeshData(points, vertices, new MeshRanges(1, 1, 2)).ranges());
        MeshFormat lines = new MeshFormat(1, MeshData.VERTEX_STRIDE, 4, bounds, MeshFormat.Topology.LINES);
        assertThrows(IllegalArgumentException.class, () -> new MeshData(lines, vertices, new MeshRanges(1, 1, 2)));
        assertEquals(new MeshRanges(2, 0, 2), new MeshData(lines, vertices, new MeshRanges(2, 0, 2)).ranges());
    }
}
