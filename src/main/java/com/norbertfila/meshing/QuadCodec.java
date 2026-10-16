package com.norbertfila.meshing;

import com.norbertfila.world.Chunk;

/**
 * Bit layout of {@link QuadMesh} records and a CPU decoder that expands them into triangles exactly as the terrain
 * vertex shaders do. The decoder serves tools and tests that need explicit vertices; rendering never calls it.
 */
public final class QuadCodec {
    public static final int CROSS_PLANE_A = 6;
    public static final int CROSS_PLANE_B = 7;
    static final int[] FRONT = {0, 1, 2, 0, 2, 3};
    static final int[] BACK = {2, 1, 0, 3, 2, 0};
    private static final int[][] CROSS_PLANES = {{0, 0, 1, 1}, {1, 0, 0, 1}};
    private static final float[] SHADE = {.77f, .77f, 1.0f, .56f, .86f, .86f};
    private static final int HEIGHT_SCALE = 65535;
    private static final int FLOW_SCALE = 32767;

    private QuadCodec() {}

    static int solidPosition(int x, int y, int z, int face, int width, int height, boolean back) {
        if (x < 0 || x > Chunk.SIZE || y < 0 || y > Chunk.SIZE || z < 0 || z > Chunk.SIZE)
            throw new IllegalArgumentException("Quad corner outside the chunk");
        if (face < 0 || face > CROSS_PLANE_B || width < 1 || width > Chunk.SIZE || height < 1 || height > Chunk.SIZE)
            throw new IllegalArgumentException("Invalid quad shape");
        return x | y << 5 | z << 10 | face << 15 | (width - 1) << 18 | (height - 1) << 22 | (back ? 1 << 26 : 0);
    }

    static int solidMaterial(int texture, int block) {
        if (texture < 0 || texture > 0xFFFF || block < 0 || block > 0xFF)
            throw new IllegalArgumentException("Texture or block out of range");
        return texture | block << 16;
    }

    static int waterCell(int x, int y, int z, int face, boolean falling, int texture) {
        if ((x | y | z) >>> 4 != 0 || face < 0 || face > 5 || texture < 0 || texture > 0xFFFF)
            throw new IllegalArgumentException("Invalid water quad");
        return x | y << 4 | z << 8 | face << 12 | (falling ? 1 << 15 : 0) | texture << 16;
    }

    static int heights(float low, float high) {
        return quantizeHeight(low) | quantizeHeight(high) << 16;
    }

    static int flow(float x, float z) {
        return (quantizeFlow(x) & 0xFFFF) | quantizeFlow(z) << 16;
    }

    private static int quantizeHeight(float height) {
        if (!(height >= 0 && height <= 1)) throw new IllegalArgumentException("Water height must be in [0, 1]");
        return Math.round(height * HEIGHT_SCALE);
    }

    private static int quantizeFlow(float value) {
        if (!(value >= -1 && value <= 1)) throw new IllegalArgumentException("Flow must be in [-1, 1]");
        return Math.round(value * FLOW_SCALE);
    }

    static float height(int packed, boolean high) {
        return (high ? packed >>> 16 : packed & 0xFFFF) / (float) HEIGHT_SCALE;
    }

    static float flow(int packed, boolean high) {
        return (short) (high ? packed >>> 16 : packed) / (float) FLOW_SCALE;
    }

    /** Appends the six vertices of every quad in {@code layer order} as position, uv, texture layer and shade. */
    public static float[] decodeTextured(QuadMesh mesh, int cx, int cy, int cz) {
        return decode(mesh, cx, cy, cz, false);
    }

    /** Like {@link #decodeTextured} but with the legacy per-face colors instead of uv, texture and shade. */
    public static float[] decodeColored(QuadMesh mesh, int cx, int cy, int cz) {
        return decode(mesh, cx, cy, cz, true);
    }

    private static float[] decode(QuadMesh mesh, int cx, int cy, int cz, boolean colored) {
        int[] records = mesh.records();
        int quads = mesh.quadCount(), ints = mesh.format().ints();
        int stride = mesh.format() == QuadMesh.Format.WATER ? 9 : colored ? 6 : 7;
        float[] out = new float[quads * QuadMesh.VERTICES_PER_QUAD * stride];
        int offset = 0;
        for (int quad = 0; quad < quads; quad++)
            for (int vertex = 0; vertex < QuadMesh.VERTICES_PER_QUAD; vertex++) {
                int base = quad * ints;
                if (mesh.format() == QuadMesh.Format.WATER)
                    decodeWaterVertex(records, base, vertex, cx, cy, cz, out, offset);
                else decodeSolidVertex(records[base], records[base + 1], vertex, cx, cy, cz, colored, out, offset);
                offset += stride;
            }
        return out;
    }

    private static void decodeSolidVertex(
            int position, int material, int vertex, int cx, int cy, int cz, boolean colored, float[] out, int o) {
        int x = position & 31, y = position >>> 5 & 31, z = position >>> 10 & 31;
        int face = position >>> 15 & 7;
        int width = (position >>> 18 & 15) + 1, height = (position >>> 22 & 15) + 1;
        boolean back = (position >>> 26 & 1) != 0;
        int texture = material & 0xFFFF;
        byte block = (byte) (material >>> 16);
        float px, py, pz, u, v, shade;
        if (face < CROSS_PLANE_A) {
            int corner = FRONT[vertex];
            boolean alongV = corner == 1 || corner == 2, alongU = corner >= 2;
            int[][] faceCorners = VisibleFaceMesher.FACES[face];
            int[] offset = new int[3];
            for (int axis = 0; axis < 3; axis++)
                offset[axis] = (alongV ? (faceCorners[1][axis] - faceCorners[0][axis]) * height : 0)
                        + (alongU ? (faceCorners[3][axis] - faceCorners[0][axis]) * width : 0);
            px = cx * Chunk.SIZE + x + offset[0];
            py = cy * Chunk.SIZE + y + offset[1];
            int top = material >>> 24 & 7;
            if (top != 0 && (face == 2 || face != 3 && alongV)) py -= 1 - top / 8f;
            pz = cz * Chunk.SIZE + z + offset[2];
            u = alongU ? width : 0;
            v = alongV ? height : 0;
            shade = SHADE[face];
        } else {
            int[] plane = CROSS_PLANES[face - CROSS_PLANE_A];
            int corner = (back ? BACK : FRONT)[vertex];
            boolean right = corner >= 2;
            int top = corner == 1 || corner == 2 ? 1 : 0;
            px = (float) ((long) cx * Chunk.SIZE + x + plane[right ? 2 : 0]);
            py = (float) ((long) cy * Chunk.SIZE + y + top);
            pz = (float) ((long) cz * Chunk.SIZE + z + plane[right ? 3 : 1]);
            u = right ? 1 : 0;
            v = 1 - top;
            shade = 1;
        }
        out[o] = px;
        out[o + 1] = py;
        out[o + 2] = pz;
        if (colored) {
            float[] color = VisibleFaceMesher.color(block, face < CROSS_PLANE_A ? face : 2);
            out[o + 3] = color[0] * shade;
            out[o + 4] = color[1] * shade;
            out[o + 5] = color[2] * shade;
        } else {
            out[o + 3] = u;
            out[o + 4] = v;
            out[o + 5] = texture;
            out[o + 6] = shade;
        }
    }

    private static void decodeWaterVertex(
            int[] records, int base, int vertex, int cx, int cy, int cz, float[] out, int o) {
        int cell = records[base];
        int x = cell & 15, y = cell >>> 4 & 15, z = cell >>> 8 & 15, face = cell >>> 12 & 7;
        boolean falling = (cell >>> 15 & 1) != 0;
        float h00 = height(records[base + 1], false), h10 = height(records[base + 1], true);
        float h11 = height(records[base + 2], false), h01 = height(records[base + 2], true);
        int[] corner = VisibleFaceMesher.FACES[face][FRONT[vertex]];
        float h = corner[1] == 0 ? 0 : corner[0] == 0 ? (corner[2] == 0 ? h00 : h01) : (corner[2] == 0 ? h10 : h11);
        out[o] = (float) ((long) cx * Chunk.SIZE + x + corner[0]);
        out[o + 1] = (float) ((long) cy * Chunk.SIZE + y) + h;
        out[o + 2] = (float) ((long) cz * Chunk.SIZE + z + corner[2]);
        if (face == 2 || face == 3) {
            out[o + 3] = corner[0];
            out[o + 4] = corner[2];
        } else {
            out[o + 3] = face < 2 ? corner[2] : corner[0];
            out[o + 4] = h;
        }
        out[o + 5] = cell >>> 16;
        out[o + 6] = SHADE[face];
        out[o + 7] = face == 2 ? flow(records[base + 3], false) : 0;
        out[o + 8] = face == 2 ? flow(records[base + 3], true) : falling ? -1 : 0;
    }
}
