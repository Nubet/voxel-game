package com.norbertfila.meshing;

import com.norbertfila.generation.biome.BiomeTint;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.BlockShape;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;
import java.util.Arrays;

public final class VisibleFaceMesher implements ChunkMesher {
    public static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final float[] GRASS_TOP = BiomeTint.grass(BiomeType.PLAINS);
    private static final float[] GRASS_SIDE = {.50f, .46f, .27f};
    private static final float[] DIRT = {.46f, .29f, .16f};
    private static final float[] STONE = {.48f, .49f, .51f};
    private static final float[] SAND = {.82f, .72f, .48f};
    private static final float[] GRAVEL = {.52f, .5f, .47f};
    private static final float[] BEDROCK = {.2f, .2f, .19f};
    private static final float[] SNOW = {.92f, .94f, .98f};
    private static final float[] LOG = {.44f, .28f, .14f};
    private static final float[] LEAVES = BiomeTint.foliage(BiomeType.PLAINS);
    private static final float[] PLANKS = {.64f, .45f, .25f};
    private static final float[] COBBLE = {.43f, .44f, .46f};
    private static final float[] BRICKS = {.58f, .32f, .25f};
    private static final float[] GLASS = {.55f, .75f, .82f};
    private static final float[] SANDSTONE = {.76f, .64f, .42f};
    private static final float[] STONE_BRICKS = {.42f, .43f, .45f};
    private static final float[] YELLOW_FLOWER = {1, .85f, .1f};
    private static final float[] RED_FLOWER = {.9f, .1f, .1f};
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
                    if (block == BlockId.AIR
                            || BlockRegistry.DEFAULT.definition(block).shape() == BlockShape.CROSS) continue;
                    for (int f = 0; f < 6; f++) {
                        if (!FaceVisibility.isVisible(block, snapshot, neighborhood, f, x, y, z)) continue;
                        float shade = f == 2 ? 1.0f : f == 3 ? .56f : f < 2 ? .77f : .86f;
                        float[] rgb = color(block, f);
                        int texture = BlockRegistry.DEFAULT.definition(block).textureLayer(f);
                        if (BiomeTint.tintedTexture(texture) && texture != 3)
                            rgb = BiomeTint.color(snapshot.tintIndex(x, z), BiomeTint.foliageTexture(texture));
                        for (int index : TRIANGLE_INDICES) {
                            int[] p = FACES[f][index];
                            out.add(cx * Chunk.SIZE + x + p[0]);
                            out.add(cy * Chunk.SIZE
                                    + y
                                    + p[1]
                                            * BlockRegistry.DEFAULT
                                                    .definition(block)
                                                    .model()
                                                    .maxY());
                            out.add(cz * Chunk.SIZE + z + p[2]);
                            out.add(rgb[0] * shade);
                            out.add(rgb[1] * shade);
                            out.add(rgb[2] * shade);
                        }
                    }
                }
        CrossPlantMesher.append(out, snapshot, cx, cy, cz, false);
        return new MeshData(out.toArray());
    }

    static float[] color(byte block, int face) {
        if (block == BlockId.GRASS) return face == 2 ? GRASS_TOP : GRASS_SIDE;
        return switch (block) {
            case BlockId.DIRT -> DIRT;
            case BlockId.STONE -> STONE;
            case BlockId.SAND -> SAND;
            case BlockId.GRAVEL -> GRAVEL;
            case BlockId.BEDROCK -> BEDROCK;
            case BlockId.SNOW -> SNOW;
            case BlockId.LOG -> LOG;
            case BlockId.LEAVES -> LEAVES;
            case BlockId.PLANKS -> PLANKS;
            case BlockId.COBBLE -> COBBLE;
            case BlockId.BRICKS -> BRICKS;
            case BlockId.GLASS -> GLASS;
            case BlockId.SANDSTONE -> SANDSTONE;
            case BlockId.STONE_BRICKS -> STONE_BRICKS;
            case BlockId.SHORT_GRASS, BlockId.FERN -> GRASS_TOP;
            case BlockId.DANDELION_YELLOW -> YELLOW_FLOWER;
            case BlockId.DANDELION_WHITE -> SNOW;
            case BlockId.POPPY -> RED_FLOWER;
            default -> STONE;
        };
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

        int size() {
            return size;
        }

        void addAll(Floats other) {
            int required = size + other.size;
            if (required > a.length) a = Arrays.copyOf(a, Math.max(required, a.length * 2));
            System.arraycopy(other.a, 0, a, size, other.size);
            size = required;
        }
    }
}
