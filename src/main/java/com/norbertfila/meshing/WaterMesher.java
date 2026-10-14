package com.norbertfila.meshing;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.world.*;

public final class WaterMesher implements ChunkMesher {
    @Override
    public MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz) {
        GreedyMesher.Scratch scratch =
                GreedyMesher.meshSolids(snapshot, neighborhood, cx, cy, cz, GreedyMesher.FLOW_STRIDE);
        FloatList out = scratch.layer(RenderLayer.TRANSLUCENT.ordinal());
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    if (snapshot.get(x, y, z) != BlockId.WATER) continue;
                    var heights = WaterSurface.corners(neighborhood::waterSample, x, y, z);
                    float flowX = (heights.h00() + heights.h01() - heights.h10() - heights.h11()) * .5f;
                    float flowZ = (heights.h00() + heights.h10() - heights.h01() - heights.h11()) * .5f;
                    float length = (float) Math.hypot(flowX, flowZ);
                    if (length > .0001f) {
                        flowX /= length;
                        flowZ /= length;
                    }
                    for (int face = 0; face < 6; face++) {
                        int[] normal = VisibleFaceMesher.NORMALS[face];
                        byte neighbor = neighborhood.block(x + normal[0], y + normal[1], z + normal[2]);
                        if (neighbor == BlockId.WATER) continue;
                        if (BlockRegistry.DEFAULT.occludesFullFace(neighbor)
                                && (face != 2
                                        || heights.h00() == 1
                                                && heights.h10() == 1
                                                && heights.h11() == 1
                                                && heights.h01() == 1)) continue;
                        float shade = face == 2 ? 1 : face == 3 ? .56f : face < 2 ? .77f : .86f;
                        float texture =
                                BlockRegistry.DEFAULT.definition(BlockId.WATER).textureLayer(face);
                        for (int index : VisibleFaceMesher.TRIANGLE_INDICES) {
                            int[] corner = VisibleFaceMesher.FACES[face][index];
                            float h = corner[1] == 0 ? 0 : heights.at(corner[0], corner[2]);
                            out.add((float) ((long) cx * Chunk.SIZE + x + corner[0]));
                            out.add((float) ((long) cy * Chunk.SIZE + y) + h);
                            out.add((float) ((long) cz * Chunk.SIZE + z + corner[2]));
                            if (face == 2 || face == 3) {
                                out.add(corner[0]);
                                out.add(corner[2]);
                            } else {
                                out.add(face < 2 ? corner[2] : corner[0]);
                                out.add(h);
                            }
                            out.add(texture);
                            out.add(shade);
                            out.add(face == 2 ? flowX : 0);
                            out.add(face == 2 ? flowZ : snapshot.waterState(x, y, z) == WaterState.FALLING ? -1 : 0);
                        }
                    }
                }
        float[] data = scratch.concatenate();
        return new MeshData(MeshFormat.flowTextured(data), data, scratch.ranges(GreedyMesher.FLOW_STRIDE));
    }
}
