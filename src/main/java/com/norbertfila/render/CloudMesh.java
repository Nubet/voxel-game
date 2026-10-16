package com.norbertfila.render;

import com.norbertfila.assets.CloudPattern;
import java.util.Arrays;
import java.util.Objects;

/** Static periodic tile: vec4 vertices contain local XYZ and face brightness. */
final class CloudMesh {
    private static final int[] TRIANGLES = {0, 1, 2, 0, 2, 3};
    private float[] vertices = new float[4096];
    private int size;

    static float[] build(CloudPattern pattern, float cellSize, float thickness) {
        Objects.requireNonNull(pattern);
        if (!Float.isFinite(cellSize) || cellSize <= 0 || !Float.isFinite(thickness) || thickness <= 0)
            throw new IllegalArgumentException("Cloud dimensions must be positive and finite");
        CloudMesh mesh = new CloudMesh();
        for (int z = 0; z < pattern.size(); z++) {
            for (int x = 0; x < pattern.size(); ) {
                if (!pattern.filled(x, z)) {
                    x++;
                    continue;
                }
                int start = x++;
                while (x < pattern.size() && pattern.filled(x, z)) x++;
                float left = start * cellSize, right = x * cellSize;
                float back = z * cellSize, front = back + cellSize;
                mesh.quad(left, 0, back, right, 0, back, right, 0, front, left, 0, front, .82f);
                mesh.quad(
                        left, thickness, back, right, thickness, back, right, thickness, front, left, thickness, front,
                        1);
            }
            for (int x = 0; x < pattern.size(); x++) {
                if (!pattern.filled(x, z)) continue;
                float left = x * cellSize, right = left + cellSize;
                float back = z * cellSize, front = back + cellSize;
                if (!pattern.filled(x - 1, z))
                    mesh.quad(left, 0, back, left, 0, front, left, thickness, front, left, thickness, back, .9f);
                if (!pattern.filled(x + 1, z))
                    mesh.quad(right, 0, back, right, 0, front, right, thickness, front, right, thickness, back, .9f);
                if (!pattern.filled(x, z - 1))
                    mesh.quad(left, 0, back, right, 0, back, right, thickness, back, left, thickness, back, .94f);
                if (!pattern.filled(x, z + 1))
                    mesh.quad(left, 0, front, right, 0, front, right, thickness, front, left, thickness, front, .94f);
            }
        }
        return Arrays.copyOf(mesh.vertices, mesh.size);
    }

    private void quad(
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float cx,
            float cy,
            float cz,
            float dx,
            float dy,
            float dz,
            float shade) {
        if (vertices.length < size + 24) vertices = Arrays.copyOf(vertices, vertices.length * 2);
        for (int corner : TRIANGLES) {
            vertices[size++] = switch (corner) {
                case 0 -> ax;
                case 1 -> bx;
                case 2 -> cx;
                default -> dx;
            };
            vertices[size++] = switch (corner) {
                case 0 -> ay;
                case 1 -> by;
                case 2 -> cy;
                default -> dy;
            };
            vertices[size++] = switch (corner) {
                case 0 -> az;
                case 1 -> bz;
                case 2 -> cz;
                default -> dz;
            };
            vertices[size++] = shade;
        }
    }
}
