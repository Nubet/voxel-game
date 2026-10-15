package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.List;
import java.util.Objects;

final class CaveCarver {
    private final TerrainSource terrain;
    private final GenerationCache<Surface> surfaces = new GenerationCache<>(2048);

    CaveCarver(TerrainSource terrain) {
        this.terrain = Objects.requireNonNull(terrain);
    }

    boolean carve(ChunkPos position, byte[] blocks, List<CavePlan.Segment> segments) {
        if (blocks.length != ChunkData.BLOCK_COUNT) throw new IllegalArgumentException("Invalid carving buffer");
        int ox = Math.multiplyExact(position.x(), Chunk.SIZE);
        int oy = Math.multiplyExact(position.y(), Chunk.SIZE);
        int oz = Math.multiplyExact(position.z(), Chunk.SIZE);
        boolean changed = false;
        Surface surface = null;
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
                            if (surface == null) surface = surface(position.x(), position.z());
                            if (!surface.allows(x, oy + y, z)) continue;
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

    boolean carves(int x, int y, int z, byte block, List<CavePlan.Segment> segments) {
        if (!carveable(block)) return false;
        for (var segment : segments)
            if (segment.contains(x + .5, y + .5, z + .5))
                return surface(Math.floorDiv(x, Chunk.SIZE), Math.floorDiv(z, Chunk.SIZE))
                        .allows(Math.floorMod(x, Chunk.SIZE), y, Math.floorMod(z, Chunk.SIZE));
        return false;
    }

    private Surface surface(int chunkX, int chunkZ) {
        return surfaces.get(chunkX, chunkZ, () -> {
            int ox = Math.multiplyExact(chunkX, Chunk.SIZE), oz = Math.multiplyExact(chunkZ, Chunk.SIZE);
            int[] heights = new int[18 * 18];
            for (int z = 0; z < 18; z++)
                for (int x = 0; x < 18; x++) {
                    long wx = (long) ox + x - 1, wz = (long) oz + z - 1;
                    heights[z * 18 + x] = wx < Integer.MIN_VALUE
                                    || wx > Integer.MAX_VALUE
                                    || wz < Integer.MIN_VALUE
                                    || wz > Integer.MAX_VALUE
                            ? Integer.MAX_VALUE
                            : terrain.sample((int) wx, (int) wz).surfaceHeight();
                }
            return new Surface(heights, terrain.sample(ox, oz).waterLevel());
        });
    }

    private record Surface(int[] heights, int waterLevel) {
        boolean allows(int x, int y, int z) {
            if (waterLevel == TerrainSummary.NO_WATER || y > (long) waterLevel + 1) return true;
            int index = (z + 1) * 18 + x + 1;
            int height = heights[index];
            // Keep three seabed layers and a side wall, including water in not-yet-generated neighboring chunks.
            if (height < waterLevel && y > (long) height - 3) return false;
            return y > waterLevel
                    || heights[index - 1] >= y
                            && heights[index + 1] >= y
                            && heights[index - 18] >= y
                            && heights[index + 18] >= y;
        }
    }

    private static int lower(double coordinate, int origin) {
        return (int) Math.clamp(Math.floor(coordinate - origin), 0, Chunk.SIZE);
    }

    private static int upper(double coordinate, int origin) {
        return (int) Math.clamp(Math.floor(coordinate - origin), -1, Chunk.SIZE - 1);
    }
}
