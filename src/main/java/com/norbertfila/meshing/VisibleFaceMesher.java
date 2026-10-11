package com.norbertfila.meshing;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;
import java.util.Arrays;

/** Baseline face culling inside one chunk; boundary faces are intentionally emitted */
public final class VisibleFaceMesher implements ChunkMesher {
    public static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final float[] GRASS_TOP = {.32f, .69f, .22f};
    private static final float[] GRASS_SIDE = {.50f, .46f, .27f};
    private static final float[] DIRT = {.46f, .29f, .16f};
    private static final float[] STONE = {.48f, .49f, .51f};
    // Each face: four corner offsets in CCW order as viewed from outside.
    static final int[][][] FACES = {
        {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
        {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
        {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
        {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
        {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
        {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}}
    };
    static final int[] TRIANGLE_INDICES = {0, 1, 2, 0, 2, 3};

    @Override
    public MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz) {
        Floats out = new Floats(4096);
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    byte block = snapshot.get(x, y, z);
                    if (block == BlockId.AIR) continue;
                    for (int f = 0; f < 6; f++) {
                        int nx = x + NORMALS[f][0], ny = y + NORMALS[f][1], nz = z + NORMALS[f][2];
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < Chunk.SIZE && ny < Chunk.SIZE && nz < Chunk.SIZE) {
                            if (BlockRegistry.DEFAULT.isOpaque(snapshot.get(nx, ny, nz))) continue;
                        } else if (isSolidNeighbor(neighborhood, f, x, y, z)) {
                            continue;
                        }
                        float shade = f == 2 ? 1.0f : f == 3 ? .56f : f < 2 ? .77f : .86f;
                        float[] rgb = color(block, f);
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

    private static boolean isSolidNeighbor(BlockNeighborhood neighborhood, int face, int x, int y, int z) {
        BlockNeighborhood.Side side = BlockNeighborhood.Side.values()[face];
        ChunkSnapshot neighbor = neighborhood.neighbor(side).orElse(null);
        if (neighbor == null) return false;
        return switch (face) {
            case 0 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(0, y, z));
            case 1 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(Chunk.SIZE - 1, y, z));
            case 2 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(x, 0, z));
            case 3 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(x, Chunk.SIZE - 1, z));
            case 4 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(x, y, 0));
            case 5 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(x, y, Chunk.SIZE - 1));
            default -> throw new AssertionError(face);
        };
    }

    static float[] color(byte block, int face) {
        if (block == BlockId.GRASS) return face == 2 ? GRASS_TOP : GRASS_SIDE;
        if (block == BlockId.DIRT) return DIRT;
        return STONE;
    }

    static final class Floats {
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
