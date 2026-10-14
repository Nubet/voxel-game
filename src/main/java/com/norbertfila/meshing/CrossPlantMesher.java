package com.norbertfila.meshing;

import com.norbertfila.world.*;

final class CrossPlantMesher {
    private static final int[][] PLANES = {{0, 0, 1, 1}, {1, 0, 0, 1}};
    private static final int[] INDICES = {0, 1, 2, 0, 2, 3, 2, 1, 0, 3, 2, 0};

    static void append(VisibleFaceMesher.Floats out, ChunkSnapshot snapshot, int cx, int cy, int cz, boolean textured) {
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    byte block = snapshot.get(x, y, z);
                    BlockDefinition definition = BlockRegistry.DEFAULT.definition(block);
                    if (definition.shape() != BlockShape.CROSS) continue;
                    for (int[] plane : PLANES)
                        for (int corner : INDICES) {
                            boolean right = corner >= 2;
                            int top = corner == 1 || corner == 2 ? 1 : 0;
                            out.add((float) ((long) cx * Chunk.SIZE + x + plane[right ? 2 : 0]));
                            out.add((float) ((long) cy * Chunk.SIZE + y + top));
                            out.add((float) ((long) cz * Chunk.SIZE + z + plane[right ? 3 : 1]));
                            if (textured) {
                                out.add(right ? 1 : 0);
                                out.add(1 - top);
                                out.add(definition.sideTexture());
                                out.add(1);
                            } else {
                                float[] color = VisibleFaceMesher.color(block, 2);
                                out.add(color[0]);
                                out.add(color[1]);
                                out.add(color[2]);
                            }
                        }
                }
    }

    private CrossPlantMesher() {}
}
