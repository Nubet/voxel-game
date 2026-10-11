package com.norbertfila.meshing;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;
import java.util.Arrays;

public final class PackedVertexMesher {

    public PackedMesh mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood) {
        Ints out = new Ints(1024);
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    byte block = snapshot.get(x, y, z);
                    if (block == BlockId.AIR) continue;
                    for (int face = 0; face < VisibleFaceMesher.NORMALS.length; face++) {
                        int nx = x + VisibleFaceMesher.NORMALS[face][0];
                        int ny = y + VisibleFaceMesher.NORMALS[face][1];
                        int nz = z + VisibleFaceMesher.NORMALS[face][2];
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < Chunk.SIZE && ny < Chunk.SIZE && nz < Chunk.SIZE) {
                            if (BlockRegistry.DEFAULT.isOpaque(snapshot.get(nx, ny, nz))) continue;
                        } else if (solidNeighbor(neighborhood, face, x, y, z)) {
                            continue;
                        }
                        for (int corner : VisibleFaceMesher.TRIANGLE_INDICES)
                            out.add(PackedVertex.pack(x, y, z, face, block, corner));
                    }
                }
        int[] vertices = out.toArray();
        return new PackedMesh(vertices, vertices.length / MeshData.VERTICES_PER_FACE);
    }

    private static boolean solidNeighbor(BlockNeighborhood neighborhood, int face, int x, int y, int z) {
        ChunkSnapshot neighbor =
                neighborhood.neighbor(BlockNeighborhood.Side.values()[face]).orElse(null);
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

    private static final class Ints {
        private int[] values;
        private int size;

        Ints(int capacity) {
            values = new int[capacity];
        }

        void add(int value) {
            if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
