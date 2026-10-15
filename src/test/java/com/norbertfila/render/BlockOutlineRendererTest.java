package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockOutlineRendererTest {
    @Test
    void geometryContainsExactlyTwelveExpandedCubeEdgesWithThreeEdgesAtEachCorner() {
        float[] vertices = BlockOutlineRenderer.vertices();
        assertEquals(24 * 3, vertices.length);
        Set<Set<List<Float>>> edges = new HashSet<>();
        Map<List<Float>, Integer> corners = new HashMap<>();
        int[] perAxis = new int[3];
        for (int offset = 0; offset < vertices.length; offset += 6) {
            var start = List.of(vertices[offset], vertices[offset + 1], vertices[offset + 2]);
            var end = List.of(vertices[offset + 3], vertices[offset + 4], vertices[offset + 5]);
            assertTrue(edges.add(Set.of(start, end)), "Duplicate edge");
            corners.merge(start, 1, Integer::sum);
            corners.merge(end, 1, Integer::sum);
            int changedAxes = 0;
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(start.get(axis) == -.002f || start.get(axis) == 1.002f);
                assertTrue(end.get(axis) == -.002f || end.get(axis) == 1.002f);
                float length = Math.abs(end.get(axis) - start.get(axis));
                if (length > 0) {
                    assertEquals(1.004, length, 1e-6);
                    changedAxes++;
                    perAxis[axis]++;
                }
            }
            assertEquals(1, changedAxes, "Edge must follow one axis");
        }
        assertEquals(12, edges.size());
        assertEquals(8, corners.size());
        assertTrue(corners.values().stream().allMatch(count -> count == 3));
        assertArrayEquals(new int[] {4, 4, 4}, perAxis);
    }
}
