package com.norbertfila.meshing;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.world.*;

/** Production mesher: greedy solid quads and per-block water quads with sloped, flowing surfaces. */
public final class WaterMesher implements LayeredMesher {
    private static final ThreadLocal<IntList> WATER_RECORDS = ThreadLocal.withInitial(IntList::new);
    private static final int[] TEXTURE = new int[6];

    static {
        for (int face = 0; face < 6; face++)
            TEXTURE[face] = BlockRegistry.DEFAULT.definition(BlockId.WATER).textureLayer(face);
    }

    @Override
    public ChunkMesh mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz, int parts) {
        ChunkMesh.checkParts(parts);
        QuadMesh solid = (parts & ChunkMesh.SOLID) == 0 ? null : GreedyMesher.meshSolids(snapshot, neighborhood);
        QuadMesh water = (parts & ChunkMesh.WATER) == 0 ? null : meshWater(snapshot, neighborhood);
        return new ChunkMesh(solid, water);
    }

    private static QuadMesh meshWater(ChunkSnapshot snapshot, BlockNeighborhood neighborhood) {
        IntList out = WATER_RECORDS.get();
        out.clear();
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
                    boolean falling = snapshot.waterState(x, y, z) == WaterState.FALLING;
                    for (int face = 0; face < 6; face++) {
                        int[] normal = VisibleFaceMesher.NORMALS[face];
                        // Unloaded chunks are drawn by far terrain, so a water side there would show as a seam.
                        if (face != 2 && neighborhood.chunkAt(x + normal[0], y + normal[1], z + normal[2]) == null)
                            continue;
                        byte neighbor = neighborhood.block(x + normal[0], y + normal[1], z + normal[2]);
                        if (neighbor == BlockId.WATER) continue;
                        if (BlockRegistry.DEFAULT.occludesFullFace(neighbor)
                                && (face != 2
                                        || heights.h00() == 1
                                                && heights.h10() == 1
                                                && heights.h11() == 1
                                                && heights.h01() == 1)) continue;
                        out.add(QuadCodec.waterCell(x, y, z, face, falling, TEXTURE[face]));
                        out.add(QuadCodec.heights(heights.h00(), heights.h10()));
                        out.add(QuadCodec.heights(heights.h11(), heights.h01()));
                        out.add(face == 2 ? QuadCodec.flow(flowX, flowZ) : 0);
                    }
                }
        int[] records = new int[out.size()];
        out.copyTo(records, 0);
        return new QuadMesh(
                QuadMesh.Format.WATER, records, new QuadRanges(0, 0, records.length / QuadMesh.Format.WATER.ints()));
    }
}
