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

    private static final int S = Chunk.SIZE;
    private static final int P = S + 2;
    private static final int[] U_AXIS = {2, 2, 2, 2, 0, 0};
    private static final int[] U_SIGN = {1, -1, -1, 1, -1, 1};
    private static final int[] V_AXIS = {1, 1, 0, 0, 1, 1};
    private static final int[] NORMAL_AXIS = {0, 0, 1, 1, 2, 2};
    private static final int[] NEIGHBOR_STEP = {1, -1, P * P, -P * P, P, -P};

    private static final boolean[] CUBE = new boolean[256];
    private static final boolean[] CROSS = new boolean[256];
    private static final boolean[] OCCLUDES = new boolean[256];
    private static final int[] LAYER = new int[256];
    private static final int[] GEOMETRY = new int[256];
    private static final int[][] TEXTURE = new int[256][6];

    static {
        for (int id = 0; id < 256; id++) {
            byte block = (byte) id;
            if (!BlockId.isValid(block)) continue;
            BlockDefinition definition = BlockRegistry.DEFAULT.definition(block);
            CUBE[id] = definition.model().shape() != BlockShape.CROSS
                    && definition.shape().geometryIndex() == 0;
            CROSS[id] = definition.model().shape() == BlockShape.CROSS;
            OCCLUDES[id] = definition.occludesFullFace();
            LAYER[id] = definition.renderLayer().ordinal();
            GEOMETRY[id] = definition.shape().geometryIndex();
            for (int face = 0; face < 6; face++) {
                TEXTURE[id][face] = definition.textureLayer(face);
            }
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /** Single-buffer colored mesh in emission order, kept for tools and tests that work with explicit vertices. */
    @Override
    public MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz) {
        Scratch scratch = build(snapshot, neighborhood, false, true);
        return new MeshData(QuadCodec.decodeColored(scratch.quads(), cx, cy, cz));
    }

    /** Textured mesh including water cubes, expanded to explicit vertices for tools and tests. */
    public MeshData meshTextured(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz) {
        QuadMesh quads = build(snapshot, neighborhood, false, false).quads();
        QuadRanges ranges = quads.ranges();
        int perQuad = QuadMesh.VERTICES_PER_QUAD;
        return MeshData.textured(
                QuadCodec.decodeTextured(quads, cx, cy, cz),
                new MeshRanges(ranges.opaque() * perQuad, ranges.cutout() * perQuad, ranges.translucent() * perQuad));
    }

    /** Opaque and cutout quads without water faces, which {@link WaterMesher} builds separately. */
    static QuadMesh meshSolids(ChunkSnapshot snapshot, BlockNeighborhood neighborhood) {
        return build(snapshot, neighborhood, true, false).quads();
    }

    private static Scratch build(
            ChunkSnapshot snapshot, BlockNeighborhood neighborhood, boolean excludeWater, boolean singleLayer) {
        Scratch scratch = SCRATCH.get();
        scratch.reset(singleLayer);
        scratch.load(snapshot, neighborhood);
        scratch.snapshot = snapshot;
        for (int face = 0; face < 6; face++) meshFace(scratch, face, excludeWater);
        appendCrossPlants(scratch);
        return scratch;
    }

    private static void meshFace(Scratch scratch, int face, boolean excludeWater) {
        byte[] grid = scratch.grid;
        int[] mask = scratch.mask;
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
                            && !(excludeWater && BlockId.isFluid(block))
                            && !OCCLUDES[Byte.toUnsignedInt(neighbor)]
                            && !(block == neighbor && (BlockId.isFluid(block) || block == BlockId.ICE));
                    int tint = com.norbertfila.generation.biome.BiomeTint.tintedTexture(TEXTURE[id][face])
                            ? scratch.snapshot.tintIndex(cell[0], cell[2])
                            : 0;
                    boolean snowySide = block == BlockId.GRASS
                            && face != 2
                            && face != 3
                            && grid[index + P * P] == BlockId.SNOW_LAYER;
                    mask[v * S + u] = visible ? id | tint << 8 | (snowySide ? 1 << 13 : 0) : 0;
                }
            for (int v = 0; v < S; v++)
                for (int u = 0; u < S; u++) {
                    int material = mask[v * S + u];
                    byte block = (byte) material;
                    if (block == BlockId.AIR) continue;
                    int width = 1;
                    while (u + width < S && mask[v * S + u + width] == material) width++;
                    int height = 1;
                    while (v + height < S
                            && rowMatches(mask, material, u, v + height, width)
                            && (face == 2
                                    || face == 3
                                    || BlockRegistry.DEFAULT
                                                    .definition(block)
                                                    .model()
                                                    .maxY()
                                            == 1)) height++;
                    for (int row = v; row < v + height; row++)
                        for (int column = u; column < u + width; column++) mask[row * S + column] = BlockId.AIR;
                    cell(face, depth, u, v, cell);
                    emitQuad(scratch, face, block, width, height, (material >>> 8) & 31, (material & (1 << 13)) != 0);
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

    private static boolean rowMatches(int[] mask, int block, int start, int row, int width) {
        for (int u = start; u < start + width; u++) if (mask[row * S + u] != block) return false;
        return true;
    }

    private static void emitQuad(
            Scratch scratch, int face, byte block, int width, int height, int tint, boolean snowySide) {
        int id = Byte.toUnsignedInt(block);
        IntList out = scratch.layer(LAYER[id]);
        int[] cell = scratch.cell;
        int[] corner = VisibleFaceMesher.FACES[face][0];
        out.add(QuadCodec.solidPosition(
                cell[0] + corner[0], cell[1] + corner[1], cell[2] + corner[2], face, width, height, false));
        int top = Math.round(BlockRegistry.DEFAULT.definition(block).model().maxY() * 8) % 8;
        int texture = snowySide ? BlockRegistry.SNOWY_GRASS_SIDE_TEXTURE : TEXTURE[id][face] | tint << 8;
        out.add(QuadCodec.solidMaterial(texture, id) | top << 24);
    }

    private static void appendCrossPlants(Scratch scratch) {
        appendDecorations(scratch);
        IntList out = scratch.layer(RenderLayer.CUTOUT.ordinal());
        byte[] grid = scratch.grid;
        for (int y = 0; y < S; y++)
            for (int z = 0; z < S; z++)
                for (int x = 0; x < S; x++) {
                    int id = Byte.toUnsignedInt(grid[padded(x, y, z)]);
                    if (!CROSS[id]) continue;
                    int material = QuadCodec.solidMaterial(TEXTURE[id][0] | scratch.snapshot.tintIndex(x, z) << 8, id);
                    for (int plane : new int[] {QuadCodec.CROSS_PLANE_A, QuadCodec.CROSS_PLANE_B})
                        for (boolean back : new boolean[] {false, true}) {
                            out.add(QuadCodec.solidPosition(x, y, z, plane, 1, 1, back));
                            out.add(material);
                        }
                }
    }

    private static void appendDecorations(Scratch scratch) {
        IntList out = scratch.layer(RenderLayer.CUTOUT.ordinal());
        for (int y = 0; y < S; y++)
            for (int z = 0; z < S; z++)
                for (int x = 0; x < S; x++) {
                    byte block = scratch.grid[padded(x, y, z)];
                    int geometry = GEOMETRY[Byte.toUnsignedInt(block)];
                    if (geometry == 0) continue;
                    var definition = BlockRegistry.DEFAULT.definition(block);
                    for (int face = 0; face < 6; face++) {
                        if (geometry <= 4 && (geometry <= 2 ? face >= 2 : face < 4)) continue;
                        int[] corner = VisibleFaceMesher.FACES[face][0];
                        out.add(QuadCodec.solidPosition(
                                x + corner[0], y + corner[1], z + corner[2], face, 1, 1, false));
                        out.add(QuadCodec.solidMaterial(definition.textureLayer(face), Byte.toUnsignedInt(block))
                                | geometry << 27);
                    }
                }
    }

    /** Per-thread working memory. Nothing in it escapes a meshing call except the arrays built from it. */
    static final class Scratch {
        private final byte[] grid = new byte[P * P * P];
        private final int[] mask = new int[S * S];
        private ChunkSnapshot snapshot;
        private final int[] cell = new int[3];
        private final IntList[] layers = {new IntList(), new IntList(), new IntList()};
        private boolean singleLayer;

        private void reset(boolean singleLayer) {
            this.singleLayer = singleLayer;
            for (IntList layer : layers) layer.clear();
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

        /** Single-layer meshes keep every quad in emission order, as the legacy colored format expects. */
        private IntList layer(int layer) {
            return layers[singleLayer ? 0 : layer];
        }

        private QuadMesh quads() {
            int[] records = new int[layers[0].size() + layers[1].size() + layers[2].size()];
            int offset = 0;
            for (IntList layer : layers) {
                layer.copyTo(records, offset);
                offset += layer.size();
            }
            int ints = QuadMesh.Format.SOLID.ints();
            return new QuadMesh(
                    QuadMesh.Format.SOLID,
                    records,
                    new QuadRanges(layers[0].size() / ints, layers[1].size() / ints, layers[2].size() / ints));
        }
    }
}
