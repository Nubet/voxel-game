package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpuMeshTest {
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
