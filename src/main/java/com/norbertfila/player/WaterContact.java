package com.norbertfila.player;

import com.norbertfila.fluid.WaterCurrent;
import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.world.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WaterContact {
    private WaterContact() {}

    public static Result sample(World world, PlayerState player) {
        var feet = player.position();
        return sample(
                world, player.body(), new PlayerState.Position(feet.x(), feet.y() + player.eyeHeight(), feet.z()));
    }

    public static Result sample(World world, Aabb body, PlayerState.Position eye) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(body);
        Objects.requireNonNull(eye);
        int minX = coordinate(body.minX()), minY = coordinate(body.minY()), minZ = coordinate(body.minZ());
        int maxX = coordinate(Math.nextDown(body.maxX())),
                maxY = coordinate(Math.nextDown(body.maxY())),
                maxZ = coordinate(Math.nextDown(body.maxZ()));
        if ((long) maxX - minX + 1 > 64
                || (long) maxY - minY + 1 > 64
                || (long) maxZ - minZ + 1 > 64
                || ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1) > 65536)
            throw new IllegalArgumentException("Water contact exceeds sampling budget");
        boolean[] available = {true};
        double volume = 0, currentX = 0, currentY = 0, currentZ = 0;
        for (long x = minX; x <= maxX; x++)
            for (long y = minY; y <= maxY; y++)
                for (long z = minZ; z <= maxZ; z++) {
                    if (read(world, x, y, z, available) < WaterState.SOURCE) continue;
                    var surface = surface(world, (int) x, (int) y, (int) z, available);
                    double low = Math.max(body.minY() - y, 0), high = Math.min(body.maxY() - y, 1);
                    double left = Math.max(body.minX() - x, 0), right = Math.min(body.maxX() - x, 1);
                    double back = Math.max(body.minZ() - z, 0), front = Math.min(body.maxZ() - z, 1);
                    double cellVolume = triangleVolume(
                            List.of(
                                    new Point(0, 0, surface.h00()),
                                    new Point(1, 0, surface.h10()),
                                    new Point(0, 1, surface.h01())),
                            left,
                            right,
                            back,
                            front,
                            low,
                            high);
                    cellVolume += triangleVolume(
                            List.of(
                                    new Point(1, 1, surface.h11()),
                                    new Point(0, 1, surface.h01()),
                                    new Point(1, 0, surface.h10())),
                            left,
                            right,
                            back,
                            front,
                            low,
                            high);
                    if (cellVolume > 0) {
                        var current = WaterCurrent.sample(world, new BlockPos((int) x, (int) y, (int) z));
                        available[0] &= current.available();
                        currentX += current.x() * cellVolume;
                        currentY += current.y() * cellVolume;
                        currentZ += current.z() * cellVolume;
                        volume += cellVolume;
                    }
                }
        int ex = coordinate(eye.x()), ey = coordinate(eye.y()), ez = coordinate(eye.z());
        boolean eyes = read(world, ex, ey, ez, available) >= WaterState.SOURCE
                && eye.y() - ey < surface(world, ex, ey, ez, available).heightAt(eye.x() - ex, eye.z() - ez);
        double bodyVolume = (body.maxX() - body.minX()) * (body.maxY() - body.minY()) * (body.maxZ() - body.minZ());
        return new Result(
                Math.clamp(volume / bodyVolume, 0, 1),
                eyes,
                available[0],
                volume > 0 ? currentX / volume : 0,
                volume > 0 ? currentY / volume : 0,
                volume > 0 ? currentZ / volume : 0);
    }

    public static List<ChunkPos> requiredChunks(PlayerState player) {
        Aabb body = player.body();
        return VoxelCollision.requiredChunks(new Aabb(
                Math.max(Integer.MIN_VALUE, Math.floor(body.minX()) - 1),
                Math.max(Integer.MIN_VALUE, Math.floor(body.minY()) - 1),
                Math.max(Integer.MIN_VALUE, Math.floor(body.minZ()) - 1),
                Math.min((double) Integer.MAX_VALUE + 1, Math.ceil(body.maxX()) + 1),
                Math.min((double) Integer.MAX_VALUE + 1, Math.ceil(body.maxY()) + 1),
                Math.min((double) Integer.MAX_VALUE + 1, Math.ceil(body.maxZ()) + 1)));
    }

    private static WaterSurface.Corners surface(World world, int x, int y, int z, boolean[] available) {
        // Relative samples avoid wrapping the full-int world at a corner or the cell above it.
        return WaterSurface.corners(
                (dx, dy, dz) -> read(world, (long) x + dx, (long) y + dy, (long) z + dz, available), 0, 0, 0);
    }

    private static int read(World world, long x, long y, long z, boolean[] available) {
        if (x < Integer.MIN_VALUE
                || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE
                || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE
                || z > Integer.MAX_VALUE) return WaterSurface.SOLID;
        BlockPos position = new BlockPos((int) x, (int) y, (int) z);
        if (!world.hasChunk(Coordinates.worldToChunk(position))) {
            available[0] = false;
            return WaterSurface.UNKNOWN;
        }
        byte block = (byte) world.getBlock(position).orElseThrow();
        if (block == BlockId.WATER) return world.getWaterState(position).orElseThrow();
        return BlockRegistry.DEFAULT.occludesFullFace(block) ? WaterSurface.SOLID : WaterSurface.EMPTY;
    }

    private static int coordinate(double value) {
        double floor = Math.floor(value);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Contact coordinate outside world");
        return (int) floor;
    }

    private static double triangleVolume(
            List<Point> triangle, double left, double right, double back, double front, double low, double high) {
        var clipped = clip(clip(clip(clip(triangle, 0, left, true), 0, right, false), 1, back, true), 1, front, false);
        return integral(clip(clipped, 2, low, true), low) - integral(clip(clipped, 2, high, true), high);
    }

    private static List<Point> clip(List<Point> polygon, int axis, double threshold, boolean greater) {
        if (polygon.isEmpty()) return polygon;
        var result = new ArrayList<Point>();
        Point previous = polygon.getLast();
        double previousDistance = (previous.component(axis) - threshold) * (greater ? 1 : -1);
        for (Point point : polygon) {
            double distance = (point.component(axis) - threshold) * (greater ? 1 : -1);
            if ((distance >= 0) != (previousDistance >= 0)) {
                double alpha = previousDistance / (previousDistance - distance);
                result.add(new Point(
                        previous.x() + (point.x() - previous.x()) * alpha,
                        previous.z() + (point.z() - previous.z()) * alpha,
                        previous.height() + (point.height() - previous.height()) * alpha));
            }
            if (distance >= 0) result.add(point);
            previous = point;
            previousDistance = distance;
        }
        return result;
    }

    private static double integral(List<Point> polygon, double threshold) {
        if (polygon.size() < 3) return 0;
        Point a = polygon.getFirst();
        double volume = 0;
        for (int i = 1; i < polygon.size() - 1; i++) {
            Point b = polygon.get(i), c = polygon.get(i + 1);
            double area = Math.abs((b.x() - a.x()) * (c.z() - a.z()) - (c.x() - a.x()) * (b.z() - a.z())) * .5;
            volume += area * ((a.height() - threshold) + (b.height() - threshold) + (c.height() - threshold)) / 3;
        }
        return volume;
    }

    private record Point(double x, double z, double height) {
        double component(int axis) {
            return axis == 0 ? x : axis == 1 ? z : height;
        }
    }

    public record Result(
            double submergedFraction,
            boolean eyesSubmerged,
            boolean available,
            double currentX,
            double currentY,
            double currentZ) {
        public Result(double submergedFraction, boolean eyesSubmerged, boolean available) {
            this(submergedFraction, eyesSubmerged, available, 0, 0, 0);
        }

        public Result {
            if (!Double.isFinite(submergedFraction) || submergedFraction < 0 || submergedFraction > 1)
                throw new IllegalArgumentException("Invalid submerged fraction");
            if (!Double.isFinite(currentX)
                    || !Double.isFinite(currentY)
                    || !Double.isFinite(currentZ)
                    || Math.sqrt(currentX * currentX + currentY * currentY + currentZ * currentZ) > 1 + 1e-9)
                throw new IllegalArgumentException("Invalid water current");
        }
    }
}
