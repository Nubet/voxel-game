package com.norbertfila.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

final class RiftPlanner {
    static final int REGION_SIZE = 1024;
    private static final int MARGIN = 160;
    private final long seed;
    private final GenerationCache<Optional<Plan>> plans = new GenerationCache<>(256);

    RiftPlanner(long seed) {
        this.seed = seed;
    }

    Plan at(int x, int z) {
        var plan = plan(Math.floorDiv(x, REGION_SIZE), Math.floorDiv(z, REGION_SIZE));
        if (plan.isEmpty()) return null;
        var value = plan.get();
        return value.contains(x, z) ? value : null;
    }

    Optional<Plan> plan(int regionX, int regionZ) {
        return plans.get(regionX, regionZ, () -> create(regionX, regionZ));
    }

    private Optional<Plan> create(int regionX, int regionZ) {
        long hash = GenerationHash.hash(seed ^ 0x6A09E667F3BCC909L, regionX, regionZ);
        if (Math.floorMod(hash, 12) != 0) return Optional.empty();
        long ox = (long) regionX * REGION_SIZE, oz = (long) regionZ * REGION_SIZE;
        if (ox < Integer.MIN_VALUE
                || ox + REGION_SIZE - 1 > Integer.MAX_VALUE
                || oz < Integer.MIN_VALUE
                || oz + REGION_SIZE - 1 > Integer.MAX_VALUE) return Optional.empty();
        var random = new SplittableRandom(hash);
        double cx = ox + random.nextInt(MARGIN, REGION_SIZE - MARGIN);
        double cz = oz + random.nextInt(MARGIN, REGION_SIZE - MARGIN);
        double angle = random.nextDouble(2 * Math.PI);
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double length = random.nextDouble(60, 140);
        double width = random.nextDouble(2.5, 7);
        double depth = random.nextInt(16) == 0 ? random.nextDouble(32, 40) : random.nextDouble(16, 32);
        var nodes = new ArrayList<Node>();
        for (int i = 0; i <= 6; i++) {
            double along = length * (i / 6.0 - .5);
            double bend = random.nextDouble(-5, 5);
            nodes.add(new Node(
                    cx + dx * along - dz * bend,
                    cz + dz * along + dx * bend,
                    width * random.nextDouble(.8, 1),
                    depth * random.nextDouble(.85, 1)));
        }
        return Optional.of(new Plan(nodes));
    }

    record Node(double x, double z, double halfWidth, double depth) {}

    record Section(double along, double lateral, double halfWidth, double depth) {}

    record Plan(List<Node> nodes, int minX, int maxX, int minZ, int maxZ) {
        Plan(List<Node> nodes) {
            this(List.copyOf(nodes), lower(nodes, true), upper(nodes, true), lower(nodes, false), upper(nodes, false));
        }

        Plan {
            nodes = List.copyOf(nodes);
        }

        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        Section section(double x, double z) {
            return section(x, z, 0);
        }

        Section section(double x, double z, double margin) {
            double nearest = Double.POSITIVE_INFINITY;
            Section selected = null;
            for (int i = 0; i < nodes.size() - 1; i++) {
                var a = nodes.get(i);
                var b = nodes.get(i + 1);
                double dx = b.x - a.x, dz = b.z - a.z;
                double t = Math.clamp(((x - a.x) * dx + (z - a.z) * dz) / (dx * dx + dz * dz), 0, 1);
                double px = x - a.x - t * dx, pz = z - a.z - t * dz;
                double distance = Math.hypot(px, pz);
                if (distance >= nearest) continue;
                nearest = distance;
                double along = (i + t) / (nodes.size() - 1);
                double taper = smooth(Math.min(along / .16, (1 - along) / .22));
                double width = (a.halfWidth + t * (b.halfWidth - a.halfWidth)) * (.25 + .75 * taper);
                selected = new Section(
                        along,
                        Math.copySign(distance, dx * pz - dz * px),
                        width,
                        (a.depth + t * (b.depth - a.depth)) * taper);
            }
            return selected != null && nearest < selected.halfWidth + margin ? selected : null;
        }

        private static int lower(List<Node> nodes, boolean x) {
            return (int) Math.floor(
                    nodes.stream().mapToDouble(n -> (x ? n.x : n.z) - 8).min().orElseThrow());
        }

        private static int upper(List<Node> nodes, boolean x) {
            return (int) Math.ceil(
                    nodes.stream().mapToDouble(n -> (x ? n.x : n.z) + 8).max().orElseThrow());
        }
    }

    static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
