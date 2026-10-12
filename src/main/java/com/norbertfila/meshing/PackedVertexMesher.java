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
                        if (!FaceVisibility.isVisible(block, snapshot, neighborhood, face, x, y, z)) continue;
                        int textureLayer =
                                BlockRegistry.DEFAULT.definition(block).textureLayer(face);
                        for (int corner : VisibleFaceMesher.TRIANGLE_INDICES)
                            out.add(PackedVertex.pack(x, y, z, face, block, corner, textureLayer));
                    }
                }
        int[] vertices = out.toArray();
        return new PackedMesh(vertices, vertices.length / MeshData.VERTICES_PER_FACE);
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
