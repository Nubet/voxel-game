package com.norbertfila.render;

import com.norbertfila.world.*;
import java.util.Arrays;

/** Baseline face culling inside one chunk; boundary faces are intentionally emitted */
public final class VisibleFaceMesher implements ChunkMesher {
    private static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    // Each face: four corner offsets in CCW order as viewed from outside.
    private static final int[][][] FACES = {
        {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
        {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
        {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
        {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
        {{1, 0, 1}, {0, 0, 1}, {0, 1, 1}, {1, 1, 1}},
        {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}
    };
    private static final int[] TRIANGLE_INDICES = {0, 1, 2, 0, 2, 3};

    @Override
    public MeshData mesh(Chunk chunk, int cx, int cy, int cz) {
        Floats out = new Floats(4096);
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    byte block = chunk.get(x, y, z);
                    if (block == BlockId.AIR) continue;
                    for (int f = 0; f < 6; f++) {
                        int nx = x + NORMALS[f][0], ny = y + NORMALS[f][1], nz = z + NORMALS[f][2];
                        if (nx >= 0
                                && ny >= 0
                                && nz >= 0
                                && nx < Chunk.SIZE
                                && ny < Chunk.SIZE
                                && nz < Chunk.SIZE
                                && chunk.get(nx, ny, nz) != BlockId.AIR) continue;
                        float[] rgb = block == BlockId.GRASS
                                ? (f == 2 ? new float[] {.32f, .69f, .22f} : new float[] {.50f, .46f, .27f})
                                : block == BlockId.DIRT
                                        ? new float[] {.46f, .29f, .16f}
                                        : new float[] {.48f, .49f, .51f};
                        float shade = f == 2 ? 1.0f : f == 3 ? .56f : f < 2 ? .77f : .86f;
                        for (int index : TRIANGLE_INDICES) {
                            int[] p = FACES[f][index];
                            out.add(cx * Chunk.SIZE + x + p[0]);
                            out.add(cy * Chunk.SIZE + y + p[1]);
                            out.add(cz * Chunk.SIZE + z + p[2]);
                            out.add(rgb[0] * shade);
                            out.add(rgb[1] * shade);
                            out.add(rgb[2] * shade);
                        }
                    }
                }
        return new MeshData(out.toArray());
    }

    private static final class Floats {
        private float[] a;
        private int size;

        Floats(int capacity) {
            a = new float[capacity];
        }

        void add(float value) {
            if (size == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[size++] = value;
        }

        float[] toArray() {
            return Arrays.copyOf(a, size);
        }
    }
}
