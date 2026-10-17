package com.norbertfila.generation;

import java.util.List;

record TreePlan(List<Branch> branches, List<Crown> crowns, byte log, byte leaves) {
    TreePlan {
        branches = List.copyOf(branches);
        crowns = List.copyOf(crowns);
        if (branches.isEmpty() || crowns.isEmpty()) throw new IllegalArgumentException("Tree needs wood and foliage");
    }

    int maxY() {
        return Math.max(
                branches.stream().mapToInt(b -> Math.max(b.y(), b.endY())).max().orElseThrow(),
                crowns.stream().mapToInt(c -> c.y() + c.height()).max().orElseThrow());
    }

    void wood(BlockSink sink) {
        for (var b : branches) {
            int x = b.x(), y = b.y(), z = b.z();
            int steps = Math.max(Math.abs(b.endX() - x), Math.max(Math.abs(b.endY() - y), Math.abs(b.endZ() - z)));
            for (int i = 0; i <= steps; i++) {
                int nx = steps == 0 ? x : b.x() + Math.round((b.endX() - b.x()) * i / (float) steps);
                int ny = steps == 0 ? y : b.y() + Math.round((b.endY() - b.y()) * i / (float) steps);
                int nz = steps == 0 ? z : b.z() + Math.round((b.endZ() - b.z()) * i / (float) steps);
                while (x != nx) {
                    sink.set(x, y, z, log);
                    x += Integer.signum(nx - x);
                }
                while (z != nz) {
                    sink.set(x, y, z, log);
                    z += Integer.signum(nz - z);
                }
                while (y != ny) {
                    sink.set(x, y, z, log);
                    y += Integer.signum(ny - y);
                }
                sink.set(x, y, z, log);
            }
        }
    }

    void foliage(BlockSink sink) {
        for (var c : crowns)
            for (int dy = -c.height(); dy <= c.height(); dy++)
                for (int dz = -c.radius(); dz <= c.radius(); dz++)
                    for (int dx = -c.radius(); dx <= c.radius(); dx++)
                        if (c.contains(c.x() + dx, c.y() + dy, c.z() + dz))
                            sink.set(c.x() + dx, c.y() + dy, c.z() + dz, leaves);
    }

    long canopyHeight(int x, int z) {
        long top = Long.MIN_VALUE;
        for (var c : crowns) {
            if (Math.abs((long) x - c.x()) > c.radius() || Math.abs((long) z - c.z()) > c.radius()) continue;
            for (int y = c.y() + c.height(); y >= c.y() - c.height(); y--)
                if (c.contains(x, y, z)) {
                    top = Math.max(top, y);
                    break;
                }
        }
        return top;
    }

    record Branch(int x, int y, int z, int endX, int endY, int endZ) {}

    record Crown(int x, int y, int z, int radius, int height, long seed) {
        boolean contains(int wx, int wy, int wz) {
            double dx = (wx - (long) x) / (double) radius;
            double dz = (wz - (long) z) / (double) radius;
            double dy = (wy - (long) y) / (height + .4);
            double distance = dx * dx + dz * dz + dy * dy;
            return distance <= .72
                    || distance <= 1 && Math.floorMod(GenerationHash.hash(seed, wx, wz * 31L + wy), 7) != 0;
        }
    }

    @FunctionalInterface
    interface BlockSink {
        void set(int x, int y, int z, byte block);
    }
}
