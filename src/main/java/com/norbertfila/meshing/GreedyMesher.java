package com.norbertfila.meshing;

import com.norbertfila.world.BlockDefinition;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.BlockShape;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;
import com.norbertfila.world.RenderLayer;

/**
 * Merges coplanar visible cube faces of the same block into rectangles. Works on a per-thread scratch: the chunk is
 * copied into a padded grid with one layer of each face neighbor, so visibility tests are plain array reads and a
 * mesh allocates only its final vertex array.
 */
public final class GreedyMesher implements ChunkMesher {
    static final int COLOR_STRIDE = MeshData.VERTEX_STRIDE;
    static final int TEXTURED_STRIDE = MeshFormat.TEXTURED_VERTEX_STRIDE;
    static final int FLOW_STRIDE = MeshFormat.FLOW_TEXTURED_VERTEX_STRIDE;

    private static final int S = Chunk.SIZE;
    private static final int P = S + 2;
    private static final int[] U_AXIS = {2, 2, 2, 2, 0, 0};
    private static final int[] U_SIGN = {1, -1, -1, 1, -1, 1};
    private static final int[] V_AXIS = {1, 1, 0, 0, 1, 1};
    private static final int[] NORMAL_AXIS = {0, 0, 1, 1, 2, 2};
    private static final int[] TRIANGLE = VisibleFaceMesher.TRIANGLE_INDICES;
    private static final int[][] CROSS_PLANES = {{0, 0, 1, 1}, {1, 0, 0, 1}};
    private static final int[] CROSS_INDICES = {0, 1, 2, 0, 2, 3, 2, 1, 0, 3, 2, 0};
    private static final float[] SHADE = {.77f, .77f, 1.0f, .56f, .86f, .86f};
    private static final int[] NEIGHBOR_STEP = {1, -1, P * P, -P * P, P, -P};

    private static final boolean[] CUBE = new boolean[256];
    private static final boolean[] CROSS = new boolean[256];
    private static final boolean[] OCCLUDES = new boolean[256];
    private static final int[] LAYER = new int[256];
    private static final float[][] TEXTURE = new float[256][6];
    private static final float[][][] COLOR = new float[256][6][];

    static {
        for (int id = 0; id < 256; id++) {
            byte block = (byte) id;
            if (!BlockId.isValid(block)) continue;
            BlockDefinition definition = BlockRegistry.DEFAULT.definition(block);
            CUBE[id] = definition.shape() == BlockShape.CUBE;
            CROSS[id] = definition.shape() == BlockShape.CROSS;
            OCCLUDES[id] = definition.occludesFullFace();
            LAYER[id] = definition.renderLayer().ordinal();
            for (int face = 0; face < 6; face++) {
                TEXTURE[id][face] = definition.textureLayer(face);
                COLOR[id][face] = VisibleFaceMesher.color(block, face);
            }
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    @Override
    public MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz) {
        Scratch scratch = build(snapshot, neighborhood, cx, cy, cz, COLOR_STRIDE, false);
        return new MeshData(scratch.concatenate());
    }

    public MeshData meshTextured(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz) {
        Scratch scratch = build(snapshot, neighborhood, cx, cy, cz, TEXTURED_STRIDE, false);
        return MeshData.textured(scratch.concatenate(), scratch.ranges(TEXTURED_STRIDE));
    }

    /**
     * Meshes cube and cross-plant geometry, leaving water to the caller, into the calling thread's scratch. The
     * returned layers stay valid until the next meshing call on this thread.
     */
    static Scratch meshSolids(
            ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz, int stride) {
        return build(snapshot, neighborhood, cx, cy, cz, stride, true);
    }

    private static Scratch build(
            ChunkSnapshot snapshot,
            BlockNeighborhood neighborhood,
            int cx,
            int cy,
            int cz,
            int stride,
            boolean excludeWater) {
        Scratch scratch = SCRATCH.get();
        scratch.reset(stride);
        scratch.load(snapshot, neighborhood);
        for (int face = 0; face < 6; face++) meshFace(scratch, cx, cy, cz, face, excludeWater);
        appendCrossPlants(scratch, cx, cy, cz);
        return scratch;
    }

    private static void meshFace(Scratch scratch, int cx, int cy, int cz, int face, boolean excludeWater) {
        byte[] grid = scratch.grid;
        byte[] mask = scratch.mask;
        int[] cell = scratch.cell;
        int step = NEIGHBOR_STEP[face];
        for (int depth = 0; depth < S; depth++) {
            for (int v = 0; v < S; v++)
                for (int u = 0; u < S; u++) {
                    cell(face, depth, u, v, cell);
                    int index = padded(cell[0], cell[1], cell[2]);
                    byte block = grid[index];
                    int id = Byte.toUnsignedInt(block);
                    byte neighbor = grid[index + step];
                    boolean visible = block != BlockId.AIR
                            && CUBE[id]
                            && !(excludeWater && block == BlockId.WATER)
                            && !OCCLUDES[Byte.toUnsignedInt(neighbor)]
                            && !(block == BlockId.WATER && neighbor == BlockId.WATER);
                    mask[v * S + u] = visible ? block : BlockId.AIR;
                }
            for (int v = 0; v < S; v++)
                for (int u = 0; u < S; u++) {
                    byte block = mask[v * S + u];
                    if (block == BlockId.AIR) continue;
                    int width = 1;
                    while (u + width < S && mask[v * S + u + width] == block) width++;
                    int height = 1;
                    while (v + height < S && rowMatches(mask, block, u, v + height, width)) height++;
                    for (int row = v; row < v + height; row++)
                        for (int column = u; column < u + width; column++) mask[row * S + column] = BlockId.AIR;
                    cell(face, depth, u, v, cell);
                    emitQuad(scratch, face, block, width, height, cx, cy, cz);
                }
        }
    }

    private static void cell(int face, int depth, int u, int v, int[] cell) {
        cell[NORMAL_AXIS[face]] = depth;
        cell[U_AXIS[face]] = U_SIGN[face] > 0 ? u : S - 1 - u;
        cell[V_AXIS[face]] = v;
    }

    private static int padded(int x, int y, int z) {
        return ((y + 1) * P + z + 1) * P + x + 1;
    }

    private static boolean rowMatches(byte[] mask, byte block, int start, int row, int width) {
        for (int u = start; u < start + width; u++) if (mask[row * S + u] != block) return false;
        return true;
    }

    private static void emitQuad(Scratch scratch, int face, byte block, int width, int height, int cx, int cy, int cz) {
        int id = Byte.toUnsignedInt(block);
        FloatList out = scratch.layer(scratch.stride == COLOR_STRIDE ? 0 : LAYER[id]);
        int[][] corners = scratch.corners;
        int[] cell = scratch.cell;
        int[][] faceCorners = VisibleFaceMesher.FACES[face];
        int uAxis = U_AXIS[face], vAxis = V_AXIS[face];
        for (int axis = 0; axis < 3; axis++) {
            int base = cell[axis] + faceCorners[0][axis];
            int uStep = (faceCorners[3][axis] - faceCorners[0][axis]) * width;
            int vStep = (faceCorners[1][axis] - faceCorners[0][axis]) * height;
            corners[0][axis] = base;
            corners[1][axis] = base + vStep;
            corners[2][axis] = base + vStep + uStep;
            corners[3][axis] = base + uStep;
        }
        float shade = SHADE[face];
        for (int index : TRIANGLE) {
            int[] corner = corners[index];
            out.add(cx * Chunk.SIZE + corner[0]);
            out.add(cy * Chunk.SIZE + corner[1]);
            out.add(cz * Chunk.SIZE + corner[2]);
            if (scratch.stride == COLOR_STRIDE) {
                float[] color = COLOR[id][face];
                out.add(color[0] * shade);
                out.add(color[1] * shade);
                out.add(color[2] * shade);
                continue;
            }
            out.add((corner[uAxis] - corners[0][uAxis]) * U_SIGN[face]);
            out.add(corner[vAxis] - corners[0][vAxis]);
            out.add(TEXTURE[id][face]);
            out.add(shade);
            if (scratch.stride == FLOW_STRIDE) {
                out.add(0);
                out.add(0);
            }
        }
    }

    private static void appendCrossPlants(Scratch scratch, int cx, int cy, int cz) {
        FloatList out = scratch.layer(scratch.stride == COLOR_STRIDE ? 0 : RenderLayer.CUTOUT.ordinal());
        byte[] grid = scratch.grid;
        for (int y = 0; y < S; y++)
            for (int z = 0; z < S; z++)
                for (int x = 0; x < S; x++) {
                    byte block = grid[padded(x, y, z)];
                    int id = Byte.toUnsignedInt(block);
                    if (!CROSS[id]) continue;
                    for (int[] plane : CROSS_PLANES)
                        for (int corner : CROSS_INDICES) {
                            boolean right = corner >= 2;
                            int top = corner == 1 || corner == 2 ? 1 : 0;
                            out.add((float) ((long) cx * Chunk.SIZE + x + plane[right ? 2 : 0]));
                            out.add((float) ((long) cy * Chunk.SIZE + y + top));
                            out.add((float) ((long) cz * Chunk.SIZE + z + plane[right ? 3 : 1]));
                            if (scratch.stride == COLOR_STRIDE) {
                                float[] color = COLOR[id][2];
                                out.add(color[0]);
                                out.add(color[1]);
                                out.add(color[2]);
                                continue;
                            }
                            out.add(right ? 1 : 0);
                            out.add(1 - top);
                            out.add(TEXTURE[id][0]);
                            out.add(1);
                            if (scratch.stride == FLOW_STRIDE) {
                                out.add(0);
                                out.add(0);
                            }
                        }
                }
    }

    /** Per-thread working memory. Nothing in it escapes a meshing call except the arrays built by the caller. */
    static final class Scratch {
        private final byte[] grid = new byte[P * P * P];
        private final byte[] mask = new byte[S * S];
        private final int[] cell = new int[3];
        private final int[][] corners = new int[4][3];
        private final FloatList[] layers = {new FloatList(), new FloatList(), new FloatList()};
        private int stride;

        private void reset(int stride) {
            this.stride = stride;
            for (FloatList layer : layers) layer.clear();
        }

        private void load(ChunkSnapshot center, BlockNeighborhood neighborhood) {
            for (int y = 0; y < S; y++)
                for (int z = 0; z < S; z++) for (int x = 0; x < S; x++) grid[padded(x, y, z)] = center.get(x, y, z);
            // Face-neighbor layers; a missing neighbor reads as air so faces toward it stay until it loads.
            for (int a = 0; a < S; a++)
                for (int b = 0; b < S; b++) {
                    grid[padded(S, a, b)] = neighborhood.block(S, a, b);
                    grid[padded(-1, a, b)] = neighborhood.block(-1, a, b);
                    grid[padded(a, S, b)] = neighborhood.block(a, S, b);
                    grid[padded(a, -1, b)] = neighborhood.block(a, -1, b);
                    grid[padded(a, b, S)] = neighborhood.block(a, b, S);
                    grid[padded(a, b, -1)] = neighborhood.block(a, b, -1);
                }
        }

        FloatList layer(int layer) {
            return layers[layer];
        }

        int stride() {
            return stride;
        }

        float[] concatenate() {
            float[] vertices = new float[layers[0].size() + layers[1].size() + layers[2].size()];
            int offset = 0;
            for (FloatList layer : layers) {
                layer.copyTo(vertices, offset);
                offset += layer.size();
            }
            return vertices;
        }

        MeshRanges ranges(int stride) {
            return new MeshRanges(layers[0].size() / stride, layers[1].size() / stride, layers[2].size() / stride);
        }
    }
}
