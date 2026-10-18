package com.norbertfila.generation;

import com.norbertfila.generation.biome.BiomeType;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;

public final class BiomeLocator {
    public enum Status {
        FOUND,
        NOT_FOUND,
        LIMIT_REACHED
    }

    public record Match(int x, int z, int surfaceY, double distance) {}

    public record Result(BiomeType biome, Status status, Optional<Match> match, int samples) {}

    private final TerrainSource terrain;

    public BiomeLocator(TerrainSource terrain) {
        this.terrain = Objects.requireNonNull(terrain);
    }

    public Result locate(BiomeType biome, double originX, double originZ, int radius, long deadlineNanos) {
        Objects.requireNonNull(biome);
        if (!Double.isFinite(originX)
                || !Double.isFinite(originZ)
                || radius < 0
                || radius > 32768
                || originX < Integer.MIN_VALUE
                || originX > Integer.MAX_VALUE
                || originZ < Integer.MIN_VALUE
                || originZ > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid biome search bounds");
        return new Search(biome, originX, originZ, radius, deadlineNanos).run();
    }

    private final class Search {
        private final BiomeType biome;
        private final double x, z;
        private final int radius;
        private final long deadline;
        private int samples;
        private Match best;

        Search(BiomeType biome, double x, double z, int radius, long deadline) {
            this.biome = biome;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.deadline = deadline;
        }

        Result run() {
            // A coarse hit is only a candidate; every closer column must be checked before claiming nearest.
            for (int r = 0; r <= radius && best == null && samples < 4096; r += 128) {
                for (int dz = -r; dz <= r && best == null; dz += 128)
                    for (int dx = -r; dx <= r; dx += 128) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        if (limited()) return result(Status.LIMIT_REACHED);
                        long sx = (long) Math.floor(x) + dx, sz = (long) Math.floor(z) + dz;
                        if (sx >= Integer.MIN_VALUE
                                && sx <= Integer.MAX_VALUE
                                && sz >= Integer.MIN_VALUE
                                && sz <= Integer.MAX_VALUE) sample((int) sx, (int) sz);
                        if (best != null) break;
                    }
            }
            var queue = new PriorityQueue<Area>(Comparator.comparingDouble(Area::distanceSquared)
                    .thenComparingInt(Area::minX)
                    .thenComparingInt(Area::minZ));
            queue.add(area(
                    (int) Math.max(Integer.MIN_VALUE, Math.floor(x) - radius),
                    (int) Math.max(Integer.MIN_VALUE, Math.floor(z) - radius),
                    (int) Math.min(Integer.MAX_VALUE, Math.floor(x) + radius),
                    (int) Math.min(Integer.MAX_VALUE, Math.floor(z) + radius)));
            while (!queue.isEmpty()) {
                if (limited()) return result(Status.LIMIT_REACHED);
                var area = queue.remove();
                double bound = best == null ? (double) radius * radius : best.distance() * best.distance();
                if (area.distanceSquared() > bound) continue;
                if (area.minX() == area.maxX() && area.minZ() == area.maxZ()) {
                    sample(area.minX(), area.minZ());
                    continue;
                }
                int midX = (int) ((long) area.minX() + ((long) area.maxX() - area.minX()) / 2);
                int midZ = (int) ((long) area.minZ() + ((long) area.maxZ() - area.minZ()) / 2);
                queue.add(area(area.minX(), area.minZ(), midX, midZ));
                if (midX < area.maxX()) queue.add(area(midX + 1, area.minZ(), area.maxX(), midZ));
                if (midZ < area.maxZ()) queue.add(area(area.minX(), midZ + 1, midX, area.maxZ()));
                if (midX < area.maxX() && midZ < area.maxZ())
                    queue.add(area(midX + 1, midZ + 1, area.maxX(), area.maxZ()));
            }
            return result(best == null ? Status.NOT_FOUND : Status.FOUND);
        }

        private boolean limited() {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            return samples >= 250_000 || System.nanoTime() >= deadline;
        }

        private void sample(int sx, int sz) {
            double distance = Math.hypot(sx + .5 - x, sz + .5 - z);
            if (distance > radius || best != null && distance > best.distance()) return;
            samples++;
            var ground = terrain.sample(sx, sz);
            if (ground.biome() == biome
                    && (best == null
                            || distance < best.distance()
                            || distance == best.distance() && (sx < best.x() || sx == best.x() && sz < best.z())))
                best = new Match(sx, sz, ground.surfaceHeight(), distance);
        }

        private Area area(int minX, int minZ, int maxX, int maxZ) {
            double dx = Math.clamp(x, minX + .5, maxX + .5) - x;
            double dz = Math.clamp(z, minZ + .5, maxZ + .5) - z;
            return new Area(minX, minZ, maxX, maxZ, dx * dx + dz * dz);
        }

        private Result result(Status status) {
            return new Result(biome, status, Optional.ofNullable(best), samples);
        }
    }

    private record Area(int minX, int minZ, int maxX, int maxZ, double distanceSquared) {}
}
