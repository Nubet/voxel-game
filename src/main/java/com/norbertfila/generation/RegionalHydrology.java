package com.norbertfila.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

final class RegionalHydrology {
    static final int CELL_SIZE = 512;
    static final int REGION_SIZE = CELL_SIZE * 4;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private final long seed;
    private final int seaLevel;
    private final DoubleBinaryOperator elevation;
    private final GenerationCache<Node> nodes = new GenerationCache<>(4096);
    private static final int TILE_SIZE = 64;
    private static final int TILES = REGION_SIZE / TILE_SIZE;
    private final GenerationCache<Plan> regions = new GenerationCache<>(128);

    RegionalHydrology(long seed, int seaLevel, DoubleBinaryOperator elevation) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.elevation = elevation;
    }

    Sample sample(double x, double z, double ground) {
        int rx = (int) Math.floor(x / REGION_SIZE), rz = (int) Math.floor(z / REGION_SIZE);
        Sample result = null;
        Plan plan = regions.get(rx, rz, () -> plan(rx, rz));
        int tx = (int) Math.floor((x - (double) rx * REGION_SIZE) / TILE_SIZE);
        int tz = (int) Math.floor((z - (double) rz * REGION_SIZE) / TILE_SIZE);
        for (Reach reach : plan.tiles().get(tz * TILES + tx)) {
            if (!reach.near(x, z)) continue;
            Sample candidate = reach.sample(x, z, ground, seaLevel);
            if (candidate != null && (result == null || candidate.proximity() < result.proximity())) result = candidate;
        }
        for (Lake lake : plan.lakes()) {
            Sample candidate = lake.sample(x, z, ground);
            if (candidate != null && (result == null || result.kind() != WaterKind.RIVER)) result = candidate;
        }
        return result == null ? new Sample(ground, seaLevel, WaterKind.OCEAN, Double.POSITIVE_INFINITY) : result;
    }

    Node node(int cellX, int cellZ) {
        return nodes.get(cellX, cellZ, () -> {
            long hash = GenerationHash.hash(seed ^ 0x6A09E667F3BCC909L, cellX, cellZ);
            double x = (long) cellX * CELL_SIZE + CELL_SIZE * (.3 + .4 * unit(hash));
            double z = (long) cellZ * CELL_SIZE + CELL_SIZE * (.3 + .4 * unit(Long.rotateLeft(hash, 29)));
            int level = Math.max(seaLevel, (int) Math.floor(elevation.applyAsDouble(x, z)) - 4);
            return new Node(cellX, cellZ, x, z, level);
        });
    }

    Node downstream(Node node) {
        Node next = null;
        double steepest = 0;
        for (int[] direction : DIRECTIONS) {
            Node candidate = node(node.cellX() + direction[0], node.cellZ() + direction[1]);
            double slope = (node.level() - (double) candidate.level())
                    / Math.hypot(node.x() - candidate.x(), node.z() - candidate.z());
            if (slope > steepest) {
                next = candidate;
                steepest = slope;
            }
        }
        return next;
    }

    private Plan plan(int rx, int rz) {
        List<Reach> reaches = new ArrayList<>();
        List<Lake> lakes = new ArrayList<>();
        for (int cz = rz * 4 - 1; cz <= rz * 4 + 4; cz++)
            for (int cx = rx * 4 - 1; cx <= rx * 4 + 4; cx++) {
                Node source = node(cx, cz);
                if (source.level() <= seaLevel) continue;
                Node target = downstream(source);
                int incoming = 0;
                for (int[] d : DIRECTIONS) {
                    Node neighbor = node(cx + d[0], cz + d[1]);
                    if (source.equals(downstream(neighbor))) incoming++;
                }
                if (target == null) {
                    if (incoming > 0) {
                        long hash = GenerationHash.hash(seed ^ 0xBB67AE8584CAA73BL, cx, cz);
                        double radius = 28 + 68 * unit(hash);
                        lakes.add(new Lake(
                                source.x(),
                                source.z(),
                                source.level(),
                                radius,
                                radius * (.55 + .4 * unit(Long.rotateLeft(hash, 23))),
                                4 + 8 * unit(Long.rotateLeft(hash, 41)),
                                unit(Long.rotateLeft(hash, 11)) * Math.PI * 2));
                    }
                    continue;
                }
                double width = 3 + 1.5 * incoming;
                double length = Math.hypot(target.x() - source.x(), target.z() - source.z());
                double slope = (source.level() - (double) target.level()) / length;
                double meander = 32 * Math.clamp(1 - slope * 8, 0, 1);
                long hash = GenerationHash.hash(seed, cx, cz);
                double phase = unit(hash) * Math.PI * 2;
                double px = source.x(), pz = source.z();
                double previousLevel = source.level();
                for (int step = 1; step <= 24; step++) {
                    double t = step / 24.0;
                    double offset = meander * Math.sin(Math.PI * t) * Math.sin(2 * Math.PI * t + phase);
                    double x = source.x() + (target.x() - source.x()) * t - (target.z() - source.z()) / length * offset;
                    double z = source.z() + (target.z() - source.z()) * t + (target.x() - source.x()) / length * offset;
                    boolean coast = elevation.applyAsDouble(x, z) <= seaLevel + 1.0;
                    double level = coast ? seaLevel : source.level() + (target.level() - (double) source.level()) * t;
                    reaches.add(new Reach(
                            px,
                            pz,
                            x,
                            z,
                            previousLevel,
                            level,
                            width,
                            3 + incoming * .5,
                            width * 5 + 24 * Math.clamp(1 - slope * 8, 0, 1)));
                    px = x;
                    pz = z;
                    previousLevel = level;
                    if (coast) break;
                }
            }
        List<List<Reach>> tiles = new ArrayList<>();
        for (int i = 0; i < TILES * TILES; i++) tiles.add(new ArrayList<>());
        double ox = (double) rx * REGION_SIZE, oz = (double) rz * REGION_SIZE;
        for (Reach reach : reaches) {
            int minX = Math.max(0, (int)
                    Math.floor((Math.min(reach.ax(), reach.bx()) - reach.valleyWidth() - ox) / TILE_SIZE));
            int maxX = Math.min(TILES - 1, (int)
                    Math.floor((Math.max(reach.ax(), reach.bx()) + reach.valleyWidth() - ox) / TILE_SIZE));
            int minZ = Math.max(0, (int)
                    Math.floor((Math.min(reach.az(), reach.bz()) - reach.valleyWidth() - oz) / TILE_SIZE));
            int maxZ = Math.min(TILES - 1, (int)
                    Math.floor((Math.max(reach.az(), reach.bz()) + reach.valleyWidth() - oz) / TILE_SIZE));
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++) tiles.get(z * TILES + x).add(reach);
        }
        return new Plan(tiles.stream().map(List::copyOf).toList(), List.copyOf(lakes));
    }

    private static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    record Node(int cellX, int cellZ, double x, double z, int level) {}

    record Sample(double height, int waterLevel, WaterKind kind, double proximity) {}

    private record Plan(List<List<Reach>> tiles, List<Lake> lakes) {}

    private record Lake(double x, double z, int level, double radiusX, double radiusZ, double depth, double phase) {
        Sample sample(double wx, double wz, double ground) {
            double dx = (wx - x) / radiusX, dz = (wz - z) / radiusZ;
            double angle = Math.atan2(dz, dx);
            double radius =
                    Math.hypot(dx, dz) / (1 + .12 * Math.sin(angle * 3 + phase) + .06 * Math.sin(angle * 5 - phase));
            if (radius >= 1.3) return null;
            double height = radius <= 1
                    ? level - depth + (depth + 2) * smooth(radius)
                    : level + 2 + (ground - level - 2) * smooth((radius - 1) / .3);
            return new Sample(height, level, WaterKind.LAKE, radius);
        }
    }

    private record Reach(
            double ax,
            double az,
            double bx,
            double bz,
            double startLevel,
            double endLevel,
            double width,
            double depth,
            double valleyWidth) {
        boolean near(double x, double z) {
            return x >= Math.min(ax, bx) - valleyWidth
                    && x <= Math.max(ax, bx) + valleyWidth
                    && z >= Math.min(az, bz) - valleyWidth
                    && z <= Math.max(az, bz) + valleyWidth;
        }

        Sample sample(double x, double z, double ground, int seaLevel) {
            double dx = bx - ax, dz = bz - az;
            double t = Math.clamp(((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz), 0, 1);
            double distance = Math.hypot(x - ax - t * dx, z - az - t * dz);
            if (distance >= valleyWidth) return null;
            double level = startLevel + (endLevel - startLevel) * t;
            double height = distance <= width + 2
                    ? level - depth + (depth + 2) * smooth(distance / (width + 2))
                    : Math.min(
                            ground,
                            level
                                    + 2
                                    + (ground - level - 2)
                                            * smooth((distance - width - 2) / (valleyWidth - width - 2)));
            boolean channel = distance <= width + 2;
            return new Sample(
                    height,
                    channel ? (int) Math.floor(level) : seaLevel,
                    channel ? WaterKind.RIVER : WaterKind.OCEAN,
                    distance / (width + 2));
        }
    }
}
