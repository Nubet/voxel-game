package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
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
    private final GenerationCache<List<Feature>> floodedPlans = new GenerationCache<>(128);

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
        var result = new ArrayList<Feature>();
        for (var feature : plans.get(rx, rz, () -> plan(rx, rz)))
            if (feature.intersectsColumn(chunkX, chunkZ)) result.add(feature);
        for (int z = rz - 1; z <= rz + 1; z++)
            for (int x = rx - 1; x <= rx + 1; x++) {
                int ownerX = x, ownerZ = z;
                for (var feature : floodedPlans.get(x, z, () -> flooded(ownerX, ownerZ)))
                    if (feature.intersectsColumn(chunkX, chunkZ)) result.add(feature);
            }
        return List.copyOf(result);
    }

    private List<Feature> flooded(int rx, int rz) {
        var result = new ArrayList<Feature>();
        var probe = new Probe();
        for (var system : regionalPlans.apply(rx, rz).systems())
            for (var flood : system.floods()) {
                var feature = flooded(flood, probe);
                if (feature != null) result.add(feature);
            }
        return List.copyOf(result);
    }

    private Feature flooded(CaveSystem.Flood flood, Probe probe) {
        var cells = containedWater(flood.seed(), flood.waterLevel(), 12, 6, probe);
        if (cells.size() < 20) return null;
        int minY = cells.stream().mapToInt(BlockPos::y).min().orElseThrow();
        if (minY == flood.waterLevel()) return null;
        return new Feature(
                (int) Math.floor(flood.seed().x()),
                flood.waterLevel(),
                (int) Math.floor(flood.seed().z()),
                minY,
                flood.waterLevel(),
                12,
                Type.FLOODED,
                BlockId.WATER,
                cells);
    }

    private List<BlockPos> containedWater(CaveSystem.Point seed, int level, int radius, int depth, Probe probe) {
        double sx = seed.x(), sz = seed.z();
        if (sx < Integer.MIN_VALUE + 16.0
                || sx > Integer.MAX_VALUE - 16.0
                || sz < Integer.MIN_VALUE + 16.0
                || sz > Integer.MAX_VALUE - 16.0) return List.of();
        int x = (int) Math.floor(sx), z = (int) Math.floor(sz);
        if (level < WorldBounds.MIN_Y + 6 || level > ground.sample(x, z).surfaceHeight() - 10) return List.of();
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var cells = new ArrayList<BlockPos>();
        queue.add(new BlockPos(x, level, z));
        while (!queue.isEmpty()) {
            var cell = queue.removeFirst();
            if (cell.y() > level || !visited.add(cell)) continue;
            byte block = probe.block(cell.x(), cell.y(), cell.z());
            if (block != BlockId.AIR) {
                if (!BlockRegistry.DEFAULT.occludesFullFace(block)) return List.of();
                continue;
            }
            // Reject the entire connected water volume if another tunnel opens its containment boundary.
            if (Math.abs(cell.x() - x) > radius
                    || Math.abs(cell.z() - z) > radius
                    || cell.y() < level - depth
                    || cells.size() >= 512) return List.of();
            cells.add(cell);
            for (int[] d : SIDES) queue.add(new BlockPos(cell.x() + d[0], cell.y(), cell.z() + d[1]));
            queue.add(new BlockPos(cell.x(), cell.y() - 1, cell.z()));
            queue.add(new BlockPos(cell.x(), cell.y() + 1, cell.z()));
        }
        return List.copyOf(cells);
    }

    private List<Feature> plan(int rx, int rz) {
        var result = new ArrayList<Feature>();
        var probe = new Probe();
        for (var segment : regionalPlans.apply(rx, rz).allSegments()) {
            if (segment.profile() != CaveProfile.CHAMBER) continue;
            int x = (int) Math.floor(segment.x()), y = (int) Math.floor(segment.y()), z = (int) Math.floor(segment.z());
            if (!owned(rx, rz, x, z) || y > ground.sample(x, z).surfaceHeight() - 10) continue;
            long hash = GenerationHash.hash(seed ^ 0x789B1CE239F36C6DL, x, z);
            if ((hash & 1) != 0) continue;
            int direction = (int) (hash >>> 8) & 3;
            int sx = (int)
                    Math.floor(segment.x() + (direction == 0 ? 1 : direction == 1 ? -1 : 0) * segment.radiusX() * .7);
            int sz = (int)
                    Math.floor(segment.z() + (direction == 2 ? 1 : direction == 3 ? -1 : 0) * segment.radiusZ() * .7);
            int sy = probe.ceiling(sx, y, sz), floor = probe.floor(sx, y, sz);
            if (floor == Integer.MIN_VALUE || sy == Integer.MIN_VALUE || sy - floor < 2 || sy - floor > 6) continue;
            var spring = new Feature(sx, sy, sz, floor + 1, sy, 0, Type.SPRING, BlockId.WATER);
            if (springFits(spring, floor, probe)) addSpaced(result, spring);
            if (result.size() >= 2) break;
        }
        for (var segment : regionalPlans.apply(rx, rz).allSegments()) {
            if (result.size() >= 6) break;
            int x = (int) Math.floor(segment.x()), y = (int) Math.floor(segment.y());
            int z = (int) Math.floor(segment.z());
            if (!owned(rx, rz, x, z)) continue;
            long hash = GenerationHash.hash(GenerationHash.hash(seed ^ 0xC6BC279692B5CC83L, x, z), y, 0);
            boolean chamber = segment.profile() == CaveProfile.CHAMBER;
            if (!chamber && (hash & 63) != 0 && (hash >>> 16 & 31) != 0) continue;
            if (y < WorldBounds.MIN_Y + 5 || y > ground.sample(x, z).surfaceHeight() - 10) continue;
            int direction = (int) (hash >>> 8) & 3;
            int dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
            int dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
            if ((segment.profile() == CaveProfile.ARCH || segment.profile() == CaveProfile.DRY_RIVER)
                    && (hash >>> 40 & 3) == 0) {
                int rubbleX = (int) Math.floor(segment.x() + dx * segment.radiusX() * .65);
                int rubbleZ = (int) Math.floor(segment.z() + dz * segment.radiusZ() * .65);
                int floor = probe.floor(rubbleX, y, rubbleZ);
                if (floor != Integer.MIN_VALUE) {
                    var rubble = new Feature(
                            rubbleX, floor + 1, rubbleZ, floor + 1, floor + 2, 1, Type.RUBBLE, BlockId.GRAVEL);
                    if (rubbleFits(rubble, probe)) addSpaced(result, rubble);
                }
            }
            if ((hash & 63) == 0) {
                int wallX = (int) Math.floor(segment.x() + dx * segment.radiusX());
                int wallZ = (int) Math.floor(segment.z() + dz * segment.radiusZ());
                addSpaced(result, new Feature(wallX, y, wallZ, y - 1, y + 1, 2, Type.GRAVEL, BlockId.GRAVEL));
            }
            if ((chamber || (hash >>> 16 & 31) == 0) && (!chamber || (hash >>> 32 & 3) != 0)) {
                int px = (int) Math.floor(segment.x() + dx * segment.radiusX() * .35);
                int pz = (int) Math.floor(segment.z() + dz * segment.radiusZ() * .35);
                int floor = probe.floor(px, y, pz);
                if (floor != Integer.MIN_VALUE) {
                    int radius = chamber && (hash >>> 24 & 1) == 0 ? 2 : 1;
                    byte fluid = floor >= -60 && floor <= -40 && (hash >>> 25 & 3) != 0 ? BlockId.LAVA : BlockId.WATER;
                    var pool = new Feature(px, floor, pz, floor, floor, radius, Type.POOL, fluid);
                    if (poolFits(pool, probe)) addSpaced(result, pool);
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean owned(int rx, int rz, int x, int z) {
        // Insets keep floor decorations in their owning region; flooded volumes use neighboring plans instead.
        long lx = (long) x - (long) rx * CavePlanner.REGION_SIZE;
        long lz = (long) z - (long) rz * CavePlanner.REGION_SIZE;
        return lx >= 10 && lx < CavePlanner.REGION_SIZE - 10 && lz >= 10 && lz < CavePlanner.REGION_SIZE - 10;
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

    private boolean rubbleFits(Feature rubble, Probe probe) {
        for (int z = rubble.z() - rubble.radius(); z <= rubble.z() + rubble.radius(); z++)
            for (int x = rubble.x() - rubble.radius(); x <= rubble.x() + rubble.radius(); x++)
                if (rubble.containsXZ(x, z)
                        && (probe.core(x, rubble.y(), z) || probe.block(x, rubble.y(), z) == BlockId.WATER))
                    return false;
        return true;
    }

    private boolean springFits(Feature spring, int floor, Probe probe) {
        if ((long) spring.x() - 16 < Integer.MIN_VALUE
                || (long) spring.x() + 16 > Integer.MAX_VALUE
                || (long) spring.z() - 16 < Integer.MIN_VALUE
                || (long) spring.z() + 16 > Integer.MAX_VALUE) return false;
        if (!probe.solid(spring.x(), spring.y(), spring.z())
                || probe.core(spring.x(), spring.y(), spring.z())
                || probe.ore(spring.x(), spring.y(), spring.z())) return false;
        for (int y = spring.minY(); y < spring.y(); y++)
            if (probe.block(spring.x(), y, spring.z()) != BlockId.AIR || probe.core(spring.x(), y, spring.z()))
                return false;
        // One possible landing step permits two seven-block runs, so validate support over their full reach.
        for (int dz = -16; dz <= 16; dz++)
            for (int dx = -16; dx <= 16; dx++) {
                if (Math.abs(dx) + Math.abs(dz) > 16) continue;
                if (!probe.solid(spring.x() + dx, floor - 1, spring.z() + dz)) return false;
            }
        return true;
    }

    boolean apply(
            ChunkPos position, byte[] blocks, List<Feature> features, List<CavePlan.Segment> segments, BitSet falling) {
        int ox = Math.multiplyExact(position.x(), Chunk.SIZE), oy = Math.multiplyExact(position.y(), Chunk.SIZE);
        int oz = Math.multiplyExact(position.z(), Chunk.SIZE);
        boolean changed = false;
        for (var feature : features) {
            if (!feature.intersectsY(oy)) continue;
            if (feature.type() == Type.FLOODED) {
                for (var cell : feature.cells()) {
                    int x = cell.x() - ox, y = cell.y() - oy, z = cell.z() - oz;
                    if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) continue;
                    int index = (y * 16 + z) * 16 + x;
                    if (blocks[index] == BlockId.AIR) {
                        blocks[index] = BlockId.WATER;
                        changed = true;
                    }
                }
                continue;
            }
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
                        boolean rubble = feature.type() == Type.RUBBLE;
                        if (previous != BlockId.STONE
                                && !(rubble && previous == BlockId.AIR)
                                && !(feature.type() == Type.SPRING && y < feature.y() && previous == BlockId.AIR))
                            continue;
                        if ((feature.type() == Type.GRAVEL || rubble) && protectedCore(segments, x, y, z)) continue;
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

        int ceiling(int x, int y, int z) {
            if (block(x, y, z) != BlockId.AIR) return Integer.MIN_VALUE;
            for (int height = 1; height <= 6; height++) if (solid(x, y + height, z)) return y + height;
            return Integer.MIN_VALUE;
        }
    }

    enum Type {
        GRAVEL,
        RUBBLE,
        POOL,
        SPRING,
        FLOODED
    }

    record Feature(
            int x, int y, int z, int minY, int maxY, int radius, Type type, byte material, List<BlockPos> cells) {
        Feature(int x, int y, int z, int minY, int maxY, int radius, Type type, byte material) {
            this(x, y, z, minY, maxY, radius, type, material, List.of());
        }

        Feature {
            cells = List.copyOf(cells);
        }

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
