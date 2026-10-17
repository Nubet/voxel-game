package com.norbertfila.player.visual.model;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CuboidMeshTest {
    @Test
    void asymmetricNetHasExplicitDirectionsUprightFacesAndOutwardWinding() {
        var mesh = CuboidMesh.vertices(new ModelCuboid(-2, 7, 11, 6, 10, 3), new UvOrigin(5, 2), 64, 32);
        // Geometric TL/TR/BR/BL viewed from outside; the bottom UV net reverses V.
        float[][] corners = {
            {4, 17, 11, -2, 17, 11, -2, 7, 11, 4, 7, 11},
            {-2, 17, 14, 4, 17, 14, 4, 7, 14, -2, 7, 14},
            {4, 17, 14, 4, 17, 11, 4, 7, 11, 4, 7, 14},
            {-2, 17, 11, -2, 17, 14, -2, 7, 14, -2, 7, 11},
            {4, 17, 14, -2, 17, 14, -2, 17, 11, 4, 17, 11},
            {4, 7, 11, -2, 7, 11, -2, 7, 14, 4, 7, 14}
        };
        float[][] rectangles = {
            {8, 5, 14, 15}, {17, 5, 23, 15}, {5, 5, 8, 15}, {14, 5, 17, 15}, {8, 2, 14, 5}, {14, 5, 20, 2}
        };
        Vector3f[] normals = {
            new Vector3f(0, 0, -1), new Vector3f(0, 0, 1), new Vector3f(1, 0, 0),
            new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, -1, 0)
        };
        int[] indices = {0, 3, 2, 0, 2, 1};
        assertEquals(180, mesh.length);
        for (var face : CuboidMesh.Face.values()) {
            int base = face.ordinal() * 30;
            var rect = rectangles[face.ordinal()];
            for (int i = 0; i < 6; i++) {
                int corner = indices[i];
                for (int axis = 0; axis < 3; axis++)
                    assertEquals(corners[face.ordinal()][corner * 3 + axis], mesh[base + i * 5 + axis]);
                assertEquals((corner == 1 || corner == 2 ? rect[2] : rect[0]) / 64, mesh[base + i * 5 + 3]);
                assertEquals((corner >= 2 ? rect[3] : rect[1]) / 32, mesh[base + i * 5 + 4]);
            }
            for (int triangle = 0; triangle < 2; triangle++) {
                int start = base + triangle * 15;
                var a = position(mesh, start);
                var normal = position(mesh, start + 5)
                        .sub(a)
                        .cross(position(mesh, start + 10).sub(a))
                        .normalize();
                assertTrue(normal.equals(normals[face.ordinal()], .00001f), face.toString());
            }
        }
    }

    @Test
    void classicPartsUseDistinctCanonicalBaseRegions() {
        int[][] origins = {{16, 16}, {0, 0}, {40, 16}, {32, 48}, {0, 16}, {16, 48}};
        for (var part : PlayerModel.PARTS) {
            assertEquals(
                    new UvOrigin(
                            origins[part.part().ordinal()][0],
                            origins[part.part().ordinal()][1]),
                    part.uvOrigin());
            var mesh = CuboidMesh.vertices(part.cuboid(), part.uvOrigin(), 64, 64);
            for (int i = 0; i < mesh.length; i += 5) {
                assertTrue(mesh[i + 3] >= 0 && mesh[i + 3] <= 1);
                assertTrue(mesh[i + 4] >= 0 && mesh[i + 4] <= 1);
            }
        }
    }

    @Test
    void rejectsNetsOutsideTexture() {
        var box = new ModelCuboid(0, 0, 0, 6, 10, 3);
        assertThrows(IllegalArgumentException.class, () -> CuboidMesh.vertices(box, new UvOrigin(0, 0), 17, 64));
        assertThrows(IllegalArgumentException.class, () -> CuboidMesh.vertices(box, new UvOrigin(0, 0), 64, 12));
        assertThrows(IllegalArgumentException.class, () -> CuboidMesh.vertices(box, new UvOrigin(0, 0), 0, 64));
    }

    private static Vector3f position(float[] mesh, int offset) {
        return new Vector3f(mesh[offset], mesh[offset + 1], mesh[offset + 2]);
    }
}
