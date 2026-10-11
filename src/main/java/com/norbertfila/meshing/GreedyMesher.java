package com.norbertfila.meshing;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;

public final class GreedyMesher implements ChunkMesher {
    private static final int[] U_AXIS = {2, 2, 2, 2, 0, 0};
    private static final int[] U_SIGN = {1, -1, -1, 1, -1, 1};
    private static final int[] V_AXIS = {1, 1, 0, 0, 1, 1};

    @Override
    public MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz) {
        VisibleFaceMesher.Floats out = new VisibleFaceMesher.Floats(1024);
        for (int face = 0; face < 6; face++) meshFace(snapshot, neighborhood, cx, cy, cz, face, out);
        return new MeshData(out.toArray());
    }

    private static void meshFace(
            ChunkSnapshot snapshot,
            BlockNeighborhood neighborhood,
            int cx,
            int cy,
            int cz,
            int face,
            VisibleFaceMesher.Floats out) {
        for (int depth = 0; depth < Chunk.SIZE; depth++) {
            byte[][] mask = new byte[Chunk.SIZE][Chunk.SIZE];
            boolean[][] used = new boolean[Chunk.SIZE][Chunk.SIZE];
            for (int v = 0; v < Chunk.SIZE; v++) {
                for (int u = 0; u < Chunk.SIZE; u++) {
                    int[] cell = cell(face, depth, u, v);
                    byte block = snapshot.get(cell[0], cell[1], cell[2]);
                    mask[v][u] = block != BlockId.AIR && !solidNeighbor(snapshot, neighborhood, face, cell)
                            ? block
                            : BlockId.AIR;
                }
            }
            for (int v = 0; v < Chunk.SIZE; v++) {
                for (int u = 0; u < Chunk.SIZE; u++) {
                    if (used[v][u] || mask[v][u] == BlockId.AIR) continue;
                    byte block = mask[v][u];
                    int width = 1;
                    while (u + width < Chunk.SIZE && !used[v][u + width] && mask[v][u + width] == block) width++;
                    int height = 1;
                    while (v + height < Chunk.SIZE && rowMatches(mask, used, block, u, v + height, width)) height++;
                    for (int row = v; row < v + height; row++)
                        for (int column = u; column < u + width; column++) used[row][column] = true;
                    emitQuad(out, face, block, cell(face, depth, u, v), width, height, cx, cy, cz);
                }
            }
        }
    }

    private static int[] cell(int face, int depth, int u, int v) {
        int[] cell = {0, 0, 0};
        cell[normalAxis(face)] = depth;
        cell[U_AXIS[face]] = U_SIGN[face] > 0 ? u : Chunk.SIZE - 1 - u;
        cell[V_AXIS[face]] = v;
        return cell;
    }

    private static int normalAxis(int face) {
        return face < 2 ? 0 : face < 4 ? 1 : 2;
    }

    private static boolean rowMatches(byte[][] mask, boolean[][] used, byte block, int start, int row, int width) {
        for (int u = start; u < start + width; u++) if (used[row][u] || mask[row][u] != block) return false;
        return true;
    }

    private static boolean solidNeighbor(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int face, int[] cell) {
        int nx = cell[0] + VisibleFaceMesher.NORMALS[face][0];
        int ny = cell[1] + VisibleFaceMesher.NORMALS[face][1];
        int nz = cell[2] + VisibleFaceMesher.NORMALS[face][2];
        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < Chunk.SIZE && ny < Chunk.SIZE && nz < Chunk.SIZE) {
            return BlockRegistry.DEFAULT.isOpaque(snapshot.get(nx, ny, nz));
        }
        ChunkSnapshot neighbor =
                neighborhood.neighbor(BlockNeighborhood.Side.values()[face]).orElse(null);
        if (neighbor == null) return false;
        return switch (face) {
            case 0 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(0, cell[1], cell[2]));
            case 1 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(Chunk.SIZE - 1, cell[1], cell[2]));
            case 2 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(cell[0], 0, cell[2]));
            case 3 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(cell[0], Chunk.SIZE - 1, cell[2]));
            case 4 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(cell[0], cell[1], 0));
            case 5 -> BlockRegistry.DEFAULT.isOpaque(neighbor.get(cell[0], cell[1], Chunk.SIZE - 1));
            default -> throw new AssertionError(face);
        };
    }

    private static void emitQuad(
            VisibleFaceMesher.Floats out,
            int face,
            byte block,
            int[] cell,
            int width,
            int height,
            int cx,
            int cy,
            int cz) {
        int[] base = add(cell, VisibleFaceMesher.FACES[face][0]);
        int[] u = subtract(VisibleFaceMesher.FACES[face][3], VisibleFaceMesher.FACES[face][0]);
        int[] v = subtract(VisibleFaceMesher.FACES[face][1], VisibleFaceMesher.FACES[face][0]);
        int[][] corners = {
            base,
            add(base, scale(v, height)),
            add(add(base, scale(v, height)), scale(u, width)),
            add(base, scale(u, width))
        };
        float[] color = VisibleFaceMesher.color(block, face);
        for (int index : VisibleFaceMesher.TRIANGLE_INDICES) {
            int[] corner = corners[index];
            float shade = face == 2 ? 1.0f : face == 3 ? .56f : face < 2 ? .77f : .86f;
            out.add(cx * Chunk.SIZE + corner[0]);
            out.add(cy * Chunk.SIZE + corner[1]);
            out.add(cz * Chunk.SIZE + corner[2]);
            out.add(color[0] * shade);
            out.add(color[1] * shade);
            out.add(color[2] * shade);
        }
    }

    private static int[] add(int[] a, int[] b) {
        return new int[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static int[] subtract(int[] a, int[] b) {
        return new int[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static int[] scale(int[] vector, int amount) {
        return new int[] {vector[0] * amount, vector[1] * amount, vector[2] * amount};
    }
}
