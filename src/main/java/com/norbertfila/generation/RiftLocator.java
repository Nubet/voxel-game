package com.norbertfila.generation;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;

public final class RiftLocator {
    public enum Status {
        FOUND,
        NOT_FOUND,
        LIMIT_REACHED
    }

    public record Match(int x, int z, int surfaceY, double distance) {}

    public record Result(Status status, Optional<Match> match) {}

    private final TerrainGenerator terrain;
    private final RiftPlanner planner;

    public RiftLocator(TerrainGenerator terrain) {
        this.terrain = Objects.requireNonNull(terrain);
        planner = new RiftPlanner(terrain.seed());
    }

    public Result locate(double originX, double originZ, int radius, long deadlineNanos) {
        if (!Double.isFinite(originX)
                || !Double.isFinite(originZ)
                || originX < Integer.MIN_VALUE
                || originX > Integer.MAX_VALUE
                || originZ < Integer.MIN_VALUE
                || originZ > Integer.MAX_VALUE
                || radius < 0
                || radius > 32768) throw new IllegalArgumentException("Invalid rift search bounds");
        int minX = (int) Math.max(Integer.MIN_VALUE, Math.floor(originX) - radius);
        int maxX = (int) Math.min(Integer.MAX_VALUE, Math.floor(originX) + radius);
        int minZ = (int) Math.max(Integer.MIN_VALUE, Math.floor(originZ) - radius);
        int maxZ = (int) Math.min(Integer.MAX_VALUE, Math.floor(originZ) + radius);
        var regions = new PriorityQueue<Region>(Comparator.comparingDouble(Region::distance)
                .thenComparingInt(Region::x)
                .thenComparingInt(Region::z));
        for (int z = Math.floorDiv(minZ, RiftPlanner.REGION_SIZE);
                z <= Math.floorDiv(maxZ, RiftPlanner.REGION_SIZE);
                z++)
            for (int x = Math.floorDiv(minX, RiftPlanner.REGION_SIZE);
                    x <= Math.floorDiv(maxX, RiftPlanner.REGION_SIZE);
                    x++) {
                double dx = Math.clamp(
                                originX,
                                (double) x * RiftPlanner.REGION_SIZE + .5,
                                ((double) x + 1) * RiftPlanner.REGION_SIZE - .5)
                        - originX;
                double dz = Math.clamp(
                                originZ,
                                (double) z * RiftPlanner.REGION_SIZE + .5,
                                ((double) z + 1) * RiftPlanner.REGION_SIZE - .5)
                        - originZ;
                double distance = Math.hypot(dx, dz);
                if (distance <= radius) regions.add(new Region(x, z, distance));
            }
        Match best = null;
        while (!regions.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (System.nanoTime() >= deadlineNanos) return new Result(Status.LIMIT_REACHED, Optional.ofNullable(best));
            var region = regions.remove();
            if (best != null && region.distance() > best.distance()) break;
            var candidate = planner.plan(region.x(), region.z());
            if (candidate.isEmpty()) continue;
            var center = candidate.get().nodes().get(3);
            int x = (int) Math.floor(center.x()), z = (int) Math.floor(center.z());
            double distance = Math.hypot(x + .5 - originX, z + .5 - originZ);
            if (distance > radius || best != null && distance > best.distance() || terrain.riftAt(x, z) == null)
                continue;
            if (best == null || distance < best.distance() || x < best.x() || x == best.x() && z < best.z())
                best = new Match(x, z, terrain.surfaceHeight(x, z), distance);
        }
        return new Result(best == null ? Status.NOT_FOUND : Status.FOUND, Optional.ofNullable(best));
    }

    private record Region(int x, int z, double distance) {}
}
