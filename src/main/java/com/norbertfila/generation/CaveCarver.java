package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.List;

final class CaveCarver {
    private CaveCarver() {}

    static boolean carve(ChunkPos position, byte[] blocks, List<CavePlan.Segment> segments) {
        if (blocks.length != ChunkData.BLOCK_COUNT) throw new IllegalArgumentException("Invalid carving buffer");
        int ox = Math.multiplyExact(position.x(), Chunk.SIZE);
        int oy = Math.multiplyExact(position.y(), Chunk.SIZE);
        int oz = Math.multiplyExact(position.z(), Chunk.SIZE);
        boolean changed = false;
        for (var segment : segments) {
            int minX = lower(segment.x() - segment.radiusXZ(), ox);
            int maxX = upper(segment.x() + segment.radiusXZ(), ox);
            int minY = lower(segment.y() - segment.radiusY(), oy);
            int maxY = upper(segment.y() + segment.radiusY(), oy);
            int minZ = lower(segment.z() - segment.radiusXZ(), oz);
            int maxZ = upper(segment.z() + segment.radiusXZ(), oz);
            for (int y = minY; y <= maxY; y++) {
                double ny = (oy + (double) y + .5 - segment.y()) / segment.radiusY();
                double ny2 = ny * ny;
                for (int z = minZ; z <= maxZ; z++) {
                    double nz = (oz + (double) z + .5 - segment.z()) / segment.radiusXZ();
                    double yz2 = ny2 + nz * nz;
                    if (yz2 >= 1) continue;
                    for (int x = minX; x <= maxX; x++) {
                        double nx = (ox + (double) x + .5 - segment.x()) / segment.radiusXZ();
                        int index = (y * Chunk.SIZE + z) * Chunk.SIZE + x;
                        if (nx * nx + yz2 < 1 && carveable(blocks[index])) {
                            blocks[index] = BlockId.AIR;
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    static boolean carveable(byte block) {
        return block == BlockId.STONE || block == BlockId.DIRT || block == BlockId.GRASS;
    }

    private static int lower(double coordinate, int origin) {
        return (int) Math.clamp(Math.floor(coordinate - origin), 0, Chunk.SIZE);
    }

    private static int upper(double coordinate, int origin) {
        return (int) Math.clamp(Math.floor(coordinate - origin), -1, Chunk.SIZE - 1);
    }
}
