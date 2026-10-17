package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.PlantRules;
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
        return block == BlockId.STONE
                || block == BlockId.DIRT
                || PlantRules.supports(block)
                || block == BlockId.CLAY
                || block == BlockId.TERRACOTTA_RED
                || block == BlockId.TERRACOTTA_ORANGE
                || block == BlockId.TERRACOTTA
                || block == BlockId.TERRACOTTA_YELLOW
                || block == BlockId.TERRACOTTA_BROWN
                || block == BlockId.TERRACOTTA_WHITE
                || block == BlockId.TERRACOTTA_LIGHT_GRAY
                || block == BlockId.RED_SAND;
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
            int[] water = new int[18 * 18];
            for (int z = 0; z < 18; z++)
                for (int x = 0; x < 18; x++) {
                    long wx = (long) ox + x - 1, wz = (long) oz + z - 1;
                    int index = z * 18 + x;
                    if (wx < Integer.MIN_VALUE
                            || wx > Integer.MAX_VALUE
                            || wz < Integer.MIN_VALUE
                            || wz > Integer.MAX_VALUE) {
                        heights[index] = Integer.MAX_VALUE;
                        water[index] = TerrainSummary.NO_WATER;
                    } else {
                        var sample = terrain.sample((int) wx, (int) wz);
                        heights[index] = sample.surfaceHeight();
                        water[index] = sample.waterLevel();
                    }
                }
            return new Surface(heights, water);
        });
    }

    private record Surface(int[] heights, int[] water) {
        boolean allows(int x, int y, int z) {
            int index = (z + 1) * 18 + x + 1;
            int height = heights[index];
            // Preserve the bed and side walls even when the adjacent water chunk has not been generated yet.
            if (height < water[index] && y > (long) height - 3 && y <= (long) water[index] + 1) return false;
            return !waterAt(index - 1, y)
                    && !waterAt(index + 1, y)
                    && !waterAt(index - 18, y)
                    && !waterAt(index + 18, y);
        }

        private boolean waterAt(int index, int y) {
            return heights[index] < y && y <= water[index];
        }
    }

    private static int lower(double coordinate, int origin) {
        return (int) Math.clamp(Math.floor(coordinate - origin), 0, Chunk.SIZE);
    }

    private static int upper(double coordinate, int origin) {
        return (int) Math.clamp(Math.floor(coordinate - origin), -1, Chunk.SIZE - 1);
    }
}
