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
    private final int maxHeight;
    private final DoubleBinaryOperator elevation;
    private final GenerationCache<Node> nodes = new GenerationCache<>(4096);
    private final GenerationCache<Path> paths = new GenerationCache<>(4096);
    private final GenerationCache<Integer> levels = new GenerationCache<>(4096);
    private static final int TILE_SIZE = 64;
    private static final int TILES = REGION_SIZE / TILE_SIZE;
    private final GenerationCache<Plan> regions = new GenerationCache<>(128);

    RegionalHydrology(long seed, int seaLevel, int maxHeight, DoubleBinaryOperator elevation) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.maxHeight = maxHeight;
        this.elevation = elevation;
    }

    Sample sample(double x, double z, double ground) {
        return sample(x, z, ground, true);
    }

    Sample sample(double x, double z, double ground, boolean rivers) {
        int rx = (int) Math.floor(x / REGION_SIZE), rz = (int) Math.floor(z / REGION_SIZE);
        Sample result = null;
        double height = ground;
        double widthScale = 1 + .2 * Math.sin(x * .025 + Math.sin(z * .017));
        Plan plan = regions.get(rx, rz, () -> plan(rx, rz));
        int tx = (int) Math.floor((x - (double) rx * REGION_SIZE) / TILE_SIZE);
        int tz = (int) Math.floor((z - (double) rz * REGION_SIZE) / TILE_SIZE);
        if (rivers)
            for (Reach reach : plan.tiles().get(tz * TILES + tx)) {
                if (!reach.near(x, z)) continue;
                Sample candidate = reach.sample(x, z, ground, seaLevel, widthScale);
                if (candidate != null) height = Math.min(height, candidate.height());
                if (candidate != null && (result == null || candidate.proximity() < result.proximity()))
                    result = candidate;
            }
        for (Lake lake : plan.lakes()) {
            Sample candidate = lake.sample(x, z, ground);
            if (candidate != null) height = Math.min(height, candidate.height());
            if (candidate != null && (result == null || result.kind() != WaterKind.RIVER)) result = candidate;
        }
        if (result != null) {
            // Keep the selected channel and its banks together; other valleys may only blend outside them.
            double bank = smooth((1.3 - result.proximity()) / .3);
            height += bank * (result.height() - height);
        }
        return result == null
                ? new Sample(ground, seaLevel, WaterKind.OCEAN, Double.POSITIVE_INFINITY)
                : new Sample(height, Math.max(seaLevel, result.waterLevel()), result.kind(), result.proximity());
    }

    Node node(int cellX, int cellZ) {
        return nodes.get(cellX, cellZ, () -> {
            long hash = GenerationHash.hash(seed ^ 0x6A09E667F3BCC909L, cellX, cellZ);
            double x = (long) cellX * CELL_SIZE + CELL_SIZE * (.3 + .4 * unit(hash));
            double z = (long) cellZ * CELL_SIZE + CELL_SIZE * (.3 + .4 * unit(Long.rotateLeft(hash, 29)));
            int level =
                    Math.max(seaLevel, Math.min(maxHeight - 4, (int) Math.floor(elevation.applyAsDouble(x, z)) - 4));
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

    private List<Node> upstream(Node node) {
        List<Node> incoming = new ArrayList<>();
        for (int[] d : DIRECTIONS) {
            Node neighbor = node(node.cellX() + d[0], node.cellZ() + d[1]);
            if (node.equals(downstream(neighbor))) incoming.add(neighbor);
        }
        return incoming;
    }

    int waterLevel(Node node) {
        return levels.get(node.cellX(), node.cellZ(), () -> {
            int level = node.level();
            if (level <= seaLevel) return level;
            // Propagate upstream low points through confluences so an outlet cannot climb out of a depression.
            for (Node parent : upstream(node)) {
                Path path = path(parent);
                if (!path.coast()) level = Math.min(level, Math.min(waterLevel(parent), path.minimumLevel()));
            }
            return level;
        });
    }

    private Path path(Node source) {
        return paths.get(source.cellX(), source.cellZ(), () -> {
            Node target = downstream(source);
            double dx = target.x() - source.x(), dz = target.z() - source.z();
            double length = Math.hypot(dx, dz);
            double slope = (source.level() - (double) target.level()) / length;
            double meander = 24 + 56 * Math.clamp(1 - slope * 4, 0, 1);
            double phase = unit(GenerationHash.hash(seed, source.cellX(), source.cellZ())) * Math.PI * 2;
            List<Point> points = new ArrayList<>();
            int minimum = source.level();
            boolean coast = false;
            for (int step = 0; step <= 64; step++) {
                double t = step / 64.0;
                double envelope = Math.sin(Math.PI * t);
                double offset = meander
                        * envelope
                        * envelope
                        * (.75 * Math.sin(2 * Math.PI * t + phase) + .25 * Math.sin(4 * Math.PI * t - phase));
                double x = source.x() + dx * t - dz / length * offset;
                double z = source.z() + dz * t + dx / length * offset;
                double ground = elevation.applyAsDouble(x, z);
                int level = Math.max(seaLevel, (int) Math.floor(ground) - 2);
                minimum = Math.min(minimum, level);
                points.add(new Point(x, z, level));
                if (ground <= seaLevel + 1.0) {
                    coast = true;
                    break;
                }
            }
            return new Path(List.copyOf(points), minimum, coast);
        });
    }

    private Plan plan(int rx, int rz) {
        List<Reach> reaches = new ArrayList<>();
        List<Lake> lakes = new ArrayList<>();
        for (int cz = rz * 4 - 1; cz <= rz * 4 + 4; cz++)
            for (int cx = rx * 4 - 1; cx <= rx * 4 + 4; cx++) {
                Node source = node(cx, cz);
                if (source.level() <= seaLevel) continue;
                Node target = downstream(source);
                int incoming = upstream(source).size();
                if (target == null) {
                    if (incoming > 0) {
                        long hash = GenerationHash.hash(seed ^ 0xBB67AE8584CAA73BL, cx, cz);
                        double radius = 28 + 68 * unit(hash);
                        lakes.add(new Lake(
                                source.x(),
                                source.z(),
                                waterLevel(source),
                                radius,
                                radius * (.55 + .4 * unit(Long.rotateLeft(hash, 23))),
                                4 + 8 * unit(Long.rotateLeft(hash, 41)),
                                unit(Long.rotateLeft(hash, 11)) * Math.PI * 2));
                    }
                    continue;
                }
                double width = 2.5 + incoming;
                double length = Math.hypot(target.x() - source.x(), target.z() - source.z());
                double slope = (source.level() - (double) target.level()) / length;
                Path path = path(source);
                double startLevel = waterLevel(source);
                double endLevel = path.coast() ? seaLevel : waterLevel(target);
                double previousLevel = startLevel;
                for (int step = 1; step < path.points().size(); step++) {
                    Point a = path.points().get(step - 1), b = path.points().get(step);
                    double t = step / (double) (path.points().size() - 1);
                    double level =
                            Math.min(previousLevel, Math.min(b.level(), startLevel + (endLevel - startLevel) * t));
                    reaches.add(new Reach(
                            a.x(),
                            a.z(),
                            b.x(),
                            b.z(),
                            previousLevel,
                            level,
                            width,
                            3 + incoming * .5,
                            width * 6 + 48 * Math.clamp(1 - slope * 4, 0, 1)));
                    previousLevel = level;
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

    private record Point(double x, double z, int level) {}

    private record Path(List<Point> points, int minimumLevel, boolean coast) {}

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
            return new Sample(
                    Math.min(ground, height), Math.min(level, (int) Math.floor(ground)), WaterKind.LAKE, radius);
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

        Sample sample(double x, double z, double ground, int seaLevel, double widthScale) {
            double dx = bx - ax, dz = bz - az;
            double t = Math.clamp(((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz), 0, 1);
            double distance = Math.hypot(x - ax - t * dx, z - az - t * dz);
            if (distance >= valleyWidth) return null;
            double level = startLevel + (endLevel - startLevel) * t;
            double channelWidth = width * widthScale;
            double bankWidth = channelWidth + 4;
            double height = distance <= bankWidth
                    ? level - depth + (depth + 2) * smooth(distance / bankWidth)
                    : Math.min(
                            ground,
                            level
                                    + 2
                                    + (ground - level - 2)
                                            * smooth((distance - bankWidth) / (valleyWidth - bankWidth)));
            boolean channel = distance <= bankWidth;
            return new Sample(
                    Math.min(ground, height),
                    channel
                            ? Math.max(seaLevel, Math.min((int) Math.floor(ground), (int) Math.floor(level)))
                            : seaLevel,
                    channel ? WaterKind.RIVER : WaterKind.OCEAN,
                    distance / bankWidth);
        }
    }
}
