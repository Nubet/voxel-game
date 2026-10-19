package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WorldBounds;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

final class CaveFeatures {
    private final long seed;
    private final GroundSampler ground;
    private final CaveCarver carver;
    private final OreGenerator ores;
    private final BiFunction<Integer, Integer, CavePlan> regionalPlans;
    private final BiFunction<Integer, Integer, List<CavePlan.Segment>> caves;
    private final GenerationCache<List<Feature>> plans = new GenerationCache<>(128);

    CaveFeatures(
            long seed,
            GroundSampler ground,
            CaveCarver carver,
            BiFunction<Integer, Integer, CavePlan> regionalPlans,
            BiFunction<Integer, Integer, List<CavePlan.Segment>> caves) {
        this.seed = seed;
        ores = new OreGenerator(seed);
        this.ground = ground;
        this.carver = carver;
        this.regionalPlans = regionalPlans;
        this.caves = caves;
    }

    List<Feature> at(int chunkX, int chunkZ) {
        int regionChunks = CavePlanner.REGION_SIZE / Chunk.SIZE;
        int rx = Math.floorDiv(chunkX, regionChunks), rz = Math.floorDiv(chunkZ, regionChunks);
        return plans.get(rx, rz, () -> plan(rx, rz)).stream()
                .filter(f -> f.intersectsColumn(chunkX, chunkZ))
                .toList();
    }

    private List<Feature> plan(int rx, int rz) {
        var result = new ArrayList<Feature>();
        var probe = new Probe();
        for (var segment : regionalPlans.apply(rx, rz).allSegments()) {
            if (result.size() >= 6) break;
            int x = (int) Math.floor(segment.x()), y = (int) Math.floor(segment.y());
            int z = (int) Math.floor(segment.z());
            // Disjoint regional interiors give neighboring feature plans a deterministic ownership boundary.
            long lx = (long) x - (long) rx * CavePlanner.REGION_SIZE;
            long lz = (long) z - (long) rz * CavePlanner.REGION_SIZE;
            if (lx < 10 || lx >= CavePlanner.REGION_SIZE - 10 || lz < 10 || lz >= CavePlanner.REGION_SIZE - 10)
                continue;
            long hash = GenerationHash.hash(GenerationHash.hash(seed ^ 0xC6BC279692B5CC83L, x, z), y, 0);
            boolean chamber = segment.profile() == CaveProfile.CHAMBER;
            if (!chamber && (hash & 63) != 0 && (hash >>> 16 & 31) != 0) continue;
            if (y < WorldBounds.MIN_Y + 5 || y > ground.sample(x, z).surfaceHeight() - 10) continue;
            int direction = (int) (hash >>> 8) & 3;
            int dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
            int dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
            if ((hash & 63) == 0) {
                int wallX = (int) Math.floor(segment.x() + dx * segment.radiusXZ());
                int wallZ = (int) Math.floor(segment.z() + dz * segment.radiusXZ());
                addSpaced(result, new Feature(wallX, y, wallZ, y - 1, y + 1, 2, Type.GRAVEL, BlockId.GRAVEL));
            }
            if ((chamber || (hash >>> 16 & 31) == 0) && (!chamber || (hash >>> 32 & 3) != 0)) {
                int px = (int) Math.floor(segment.x() + dx * segment.radiusXZ() * .35);
                int pz = (int) Math.floor(segment.z() + dz * segment.radiusXZ() * .35);
                int floor = probe.floor(px, y, pz);
                if (floor != Integer.MIN_VALUE) {
                    int radius = chamber && (hash >>> 24 & 1) == 0 ? 2 : 1;
                    byte fluid = floor >= -60 && floor <= -40 && (hash >>> 25 & 3) != 0 ? BlockId.LAVA : BlockId.WATER;
                    var pool = new Feature(px, floor, pz, floor, floor, radius, Type.POOL, fluid);
                    if (poolFits(pool, probe)) addSpaced(result, pool);
                }
            }
            if (chamber && (hash >>> 32 & 3) == 0) {
                int sx = (int) Math.floor(segment.x() + dx * segment.radiusXZ() * .55);
                int sz = (int) Math.floor(segment.z() + dz * segment.radiusXZ() * .55);
                int sy = y + 3;
                int floor = probe.floor(sx, sy - 1, sz);
                if (floor != Integer.MIN_VALUE && sy - floor >= 2 && sy - floor <= 6) {
                    var spring = new Feature(sx, sy, sz, floor + 1, sy, 0, Type.SPRING, BlockId.WATER);
                    if (springFits(spring, floor, probe)) addSpaced(result, spring);
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addSpaced(List<Feature> features, Feature candidate) {
        for (var feature : features) {
            long dx = (long) feature.x() - candidate.x(), dz = (long) feature.z() - candidate.z();
            if (dx * dx + dz * dz < 16 * 16 && Math.abs(feature.y() - candidate.y()) < 12) return;
        }
        features.add(candidate);
    }

    private boolean poolFits(Feature pool, Probe probe) {
        for (int z = pool.z() - pool.radius(); z <= pool.z() + pool.radius(); z++)
            for (int x = pool.x() - pool.radius(); x <= pool.x() + pool.radius(); x++) {
                if (!pool.containsXZ(x, z)) continue;
                if (!probe.solid(x, pool.y(), z)
                        || !probe.solid(x, pool.y() - 1, z)
                        || probe.block(x, pool.y() + 1, z) != BlockId.AIR
                        || probe.core(x, pool.y(), z)) return false;
                for (int[] d : SIDES)
                    if (!pool.containsXZ(x + d[0], z + d[1]) && !probe.solid(x + d[0], pool.y(), z + d[1]))
                        return false;
            }
        return true;
    }

    private boolean springFits(Feature spring, int floor, Probe probe) {
        if (!probe.solid(spring.x(), spring.y(), spring.z())
                || probe.core(spring.x(), spring.y(), spring.z())
                || probe.ore(spring.x(), spring.y(), spring.z())) return false;
        for (int y = spring.minY(); y < spring.y(); y++)
            if (probe.block(spring.x(), y, spring.z()) != BlockId.AIR || probe.core(spring.x(), y, spring.z()))
                return false;
        // A solid landing across the entire horizontal flow reach prevents cascades into lower tunnels.
        for (int z = spring.z() - 8; z <= spring.z() + 8; z++)
            for (int x = spring.x() - 8; x <= spring.x() + 8; x++) if (!probe.solid(x, floor, z)) return false;
        return true;
    }

    boolean apply(
            ChunkPos position, byte[] blocks, List<Feature> features, List<CavePlan.Segment> segments, BitSet falling) {
        int ox = Math.multiplyExact(position.x(), Chunk.SIZE), oy = Math.multiplyExact(position.y(), Chunk.SIZE);
        int oz = Math.multiplyExact(position.z(), Chunk.SIZE);
        boolean changed = false;
        for (var feature : features) {
            if (!feature.intersectsY(oy)) continue;
            for (int y = Math.max(oy, feature.minY()); y <= Math.min(oy + 15, feature.maxY()); y++)
                for (int z = Math.max(oz, feature.z() - feature.radius());
                        z <= Math.min(oz + 15, feature.z() + feature.radius());
                        z++)
                    for (int x = Math.max(ox, feature.x() - feature.radius());
                            x <= Math.min(ox + 15, feature.x() + feature.radius());
                            x++) {
                        if (!feature.containsXZ(x, z)) continue;
                        int index = ((y - oy) * 16 + z - oz) * 16 + x - ox;
                        byte previous = blocks[index];
                        if (previous != BlockId.STONE
                                && !(feature.type() == Type.SPRING && y < feature.y() && previous == BlockId.AIR))
                            continue;
                        if (feature.type() == Type.GRAVEL && protectedCore(segments, x, y, z)) continue;
                        blocks[index] = feature.material();
                        if (feature.type() == Type.SPRING && y < feature.y()) falling.set(index);
                        changed = true;
                    }
        }
        return changed;
    }

    private static boolean protectedCore(List<CavePlan.Segment> segments, int x, int y, int z) {
        for (var segment : segments) if (segment.coreContains(x + .5, y + .5, z + .5)) return true;
        return false;
    }

    private static final int[][] SIDES = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final class Probe {
        private final Map<Long, TerrainSummary> groundColumns = new HashMap<>();
        private final Map<Long, List<CavePlan.Segment>> caveColumns = new HashMap<>();
        private final Map<ChunkPos, byte[]> oreChunks = new HashMap<>();

        boolean ore(int x, int y, int z) {
            var chunk = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
            byte[] blocks = oreChunks.computeIfAbsent(chunk, position -> {
                byte[] candidates = new byte[ChunkData.BLOCK_COUNT];
                java.util.Arrays.fill(candidates, BlockId.STONE);
                ores.generate(position, candidates);
                return candidates;
            });
            return blocks[(Math.floorMod(y, 16) * 16 + Math.floorMod(z, 16)) * 16 + Math.floorMod(x, 16)]
                    != BlockId.STONE;
        }

        byte block(int x, int y, int z) {
            long key = (long) x << 32 | Integer.toUnsignedLong(z);
            byte base = groundColumns
                    .computeIfAbsent(key, ignored -> ground.sample(x, z))
                    .materialAt(y);
            return carver.carves(x, y, z, base, segments(x, z)) ? BlockId.AIR : base;
        }

        boolean solid(int x, int y, int z) {
            return WorldBounds.containsY(y) && block(x, y, z) == BlockId.STONE;
        }

        boolean core(int x, int y, int z) {
            return protectedCore(segments(x, z), x, y, z);
        }

        List<CavePlan.Segment> segments(int x, int z) {
            int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
            long key = (long) cx << 32 | Integer.toUnsignedLong(cz);
            return caveColumns.computeIfAbsent(key, ignored -> caves.apply(cx, cz));
        }

        int floor(int x, int y, int z) {
            if (block(x, y, z) != BlockId.AIR) return Integer.MIN_VALUE;
            for (int depth = 1; depth <= 8; depth++) {
                if (solid(x, y - depth, z)) return y - depth;
                if (block(x, y - depth, z) != BlockId.AIR) break;
            }
            return Integer.MIN_VALUE;
        }
    }

    enum Type {
        GRAVEL,
        POOL,
        SPRING
    }

    record Feature(int x, int y, int z, int minY, int maxY, int radius, Type type, byte material) {
        boolean containsXZ(int wx, int wz) {
            long dx = (long) wx - x, dz = (long) wz - z;
            return dx * dx + dz * dz <= radius * radius;
        }

        boolean intersectsColumn(int cx, int cz) {
            return (long) x + radius >= (long) cx * 16
                    && (long) x - radius < (long) cx * 16 + 16
                    && (long) z + radius >= (long) cz * 16
                    && (long) z - radius < (long) cz * 16 + 16;
        }

        boolean intersectsY(int originY) {
            return maxY >= originY && minY < (long) originY + 16;
        }
    }
}
