package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.meshing.MeshFormat;
import com.norbertfila.meshing.MeshRanges;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpuMeshTest {
    @Test
    void retainsAllLayerRangesWithOneResourceOwner() {
        MeshData data = MeshData.textured(new float[18 * MeshFormat.TEXTURED_VERTEX_STRIDE], new MeshRanges(6, 6, 6));
        List<String> deleted = new ArrayList<>();
        GpuMesh mesh = new GpuMesh(7, 11, data, (vao, vbo) -> deleted.add(vao + ":" + vbo));
        assertEquals(data.format(), mesh.format());
        assertEquals(data.ranges(), mesh.ranges());
        assertEquals(18, mesh.vertexCount());
        mesh.close();
        mesh.close();
        assertEquals(List.of("7:11"), deleted);
    }

    @Test
    void closeDeletesResourcesOnlyOnce() {
        List<String> deleted = new ArrayList<>();
        GpuMesh mesh = new GpuMesh(7, 11, 18, (vao, vbo) -> deleted.add(vao + ":" + vbo));

        mesh.close();
        mesh.close();

        assertTrue(mesh.closed());
        assertEquals(List.of("7:11"), deleted);
    }
}
