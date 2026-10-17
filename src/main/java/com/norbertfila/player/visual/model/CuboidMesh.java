package com.norbertfila.player.visual.model;

import java.util.Objects;

public final class CuboidMesh {
    public static final int FLOATS_PER_VERTEX = 5;
    public static final int VERTICES_PER_FACE = 6;
    public static final int VERTEX_COUNT = 36;

    // Directions are character-local: forward -Z, right +X, up +Y.
    public enum Face {
        FRONT,
        BACK,
        RIGHT,
        LEFT,
        TOP,
        BOTTOM
    }

    private CuboidMesh() {}

    // Interleaved XYZ/UV, outward CCW triangles; UV origin is the image's top left (no image flip).
    public static float[] vertices(ModelCuboid box, UvOrigin origin, int textureWidth, int textureHeight) {
        return vertices(box, origin, textureWidth, textureHeight, 0);
    }

    public static float[] vertices(
            ModelCuboid box, UvOrigin origin, int textureWidth, int textureHeight, float inflation) {
        Objects.requireNonNull(box);
        Objects.requireNonNull(origin);
        if (!Float.isFinite(inflation) || inflation < 0) throw new IllegalArgumentException("Invalid cuboid inflation");
        float w = box.width(), h = box.height(), d = box.depth();
        if (textureWidth <= 0
                || textureHeight <= 0
                || origin.u() + 2 * (w + d) > textureWidth
                || origin.v() + d + h > textureHeight)
            throw new IllegalArgumentException("Cuboid UV net exceeds texture bounds");
        float x = box.x() - inflation, y = box.y() - inflation, z = box.z() - inflation;
        float X = box.x() + w + inflation, Y = box.y() + h + inflation, Z = box.z() + d + inflation;
        float[][] corners = {
            {X, Y, z, x, Y, z, x, y, z, X, y, z},
            {x, Y, Z, X, Y, Z, X, y, Z, x, y, Z},
            {X, Y, Z, X, Y, z, X, y, z, X, y, Z},
            {x, Y, z, x, Y, Z, x, y, Z, x, y, z},
            {X, Y, Z, x, Y, Z, x, Y, z, X, Y, z},
            {X, y, z, x, y, z, x, y, Z, X, y, Z}
        };
        float u = origin.u(), v = origin.v();
        float[][] rectangles = {
            {u + d, v + d, w, h},
            {u + 2 * d + w, v + d, w, h},
            {u, v + d, d, h},
            {u + d + w, v + d, d, h},
            {u + d, v, w, d},
            // The skin net reverses V on the underside, unlike the top cap.
            {u + d + w, v + d, w, -d}
        };
        int[] triangleCorners = {0, 3, 2, 0, 2, 1};
        float[] result = new float[VERTEX_COUNT * FLOATS_PER_VERTEX];
        int offset = 0;
        for (Face face : Face.values()) {
            float[] positions = corners[face.ordinal()], rect = rectangles[face.ordinal()];
            for (int corner : triangleCorners) {
                for (int axis = 0; axis < 3; axis++) result[offset++] = positions[corner * 3 + axis];
                result[offset++] = (rect[0] + (corner == 1 || corner == 2 ? rect[2] : 0)) / textureWidth;
                result[offset++] = (rect[1] + (corner >= 2 ? rect[3] : 0)) / textureHeight;
            }
        }
        return result;
    }
}
