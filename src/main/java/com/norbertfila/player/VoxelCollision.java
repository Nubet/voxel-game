package com.norbertfila.player;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VoxelCollision {
    private static final double SUPPORT_AND_STEP_MARGIN = 0.6;
    private static final int MAX_BLOCKS = 262144;
    private static final int MAX_CHUNKS = 512;

    public static Aabb requiredRegion(Aabb body, double x, double y, double z) {
        return body.swept(x, y, z).expandVertical(SUPPORT_AND_STEP_MARGIN, SUPPORT_AND_STEP_MARGIN);
    }

    public static List<ChunkPos> requiredChunks(Aabb region) {
        Bounds blocks = bounds(region);
        int minX = Math.floorDiv(blocks.minX, Chunk.SIZE), maxX = Math.floorDiv(blocks.maxX, Chunk.SIZE);
        int minY = Math.floorDiv(blocks.minY, Chunk.SIZE), maxY = Math.floorDiv(blocks.maxY, Chunk.SIZE);
        int minZ = Math.floorDiv(blocks.minZ, Chunk.SIZE), maxZ = Math.floorDiv(blocks.maxZ, Chunk.SIZE);
        checkVolume(minX, minY, minZ, maxX, maxY, maxZ, MAX_CHUNKS);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) chunks.add(new ChunkPos(x, y, z));
        return List.copyOf(chunks);
    }

    public boolean available(World world, Aabb region) {
        Objects.requireNonNull(world);
        return requiredChunks(region).stream().allMatch(world::hasChunk);
    }

    public boolean spaceFree(World world, Aabb body) {
        if (!available(world, body)) return false;
        Bounds range = bounds(body);
        for (long y = range.minY; y <= range.maxY; y++)
            for (long z = range.minZ; z <= range.maxZ; z++)
                for (long x = range.minX; x <= range.maxX; x++)
                    if (solid(world, (int) x, (int) y, (int) z)) return false;
        return true;
    }

    public Result move(World world, Aabb body, double x, double y, double z) {
        if (!available(world, requiredRegion(body, x, y, z)))
            return new Result(body, 0, 0, 0, false, false, false, false, false);
        Bounds candidates = bounds(body.swept(x, y, z));
        double allowedY = clip(world, body, candidates, y, Axis.Y);
        Aabb moved = body.move(0, allowedY, 0);
        double allowedX = clip(world, moved, candidates, x, Axis.X);
        moved = moved.move(allowedX, 0, 0);
        double allowedZ = clip(world, moved, candidates, z, Axis.Z);
        moved = moved.move(0, 0, allowedZ);
        return new Result(
                moved,
                allowedX,
                allowedY,
                allowedZ,
                allowedX != x,
                allowedY != y,
                allowedZ != z,
                allowedY != y && y < 0,
                true);
    }

    private static double clip(World world, Aabb body, Bounds range, double delta, Axis axis) {
        if (delta == 0) return 0;
        for (long y = range.minY; y <= range.maxY; y++) {
            for (long z = range.minZ; z <= range.maxZ; z++) {
                for (long x = range.minX; x <= range.maxX; x++) {
                    if (!solid(world, (int) x, (int) y, (int) z)) continue;
                    double min, max, blockMin;
                    boolean overlaps;
                    switch (axis) {
                        case X -> {
                            min = body.minX();
                            max = body.maxX();
                            blockMin = x;
                            overlaps = body.maxY() > y
                                    && body.minY() < y + 1.0
                                    && body.maxZ() > z
                                    && body.minZ() < z + 1.0;
                        }
                        case Y -> {
                            min = body.minY();
                            max = body.maxY();
                            blockMin = y;
                            overlaps = body.maxX() > x
                                    && body.minX() < x + 1.0
                                    && body.maxZ() > z
                                    && body.minZ() < z + 1.0;
                        }
                        case Z -> {
                            min = body.minZ();
                            max = body.maxZ();
                            blockMin = z;
                            overlaps = body.maxX() > x
                                    && body.minX() < x + 1.0
                                    && body.maxY() > y
                                    && body.minY() < y + 1.0;
                        }
                        default -> throw new AssertionError(axis);
                    }
                    if (!overlaps) continue;
                    if (delta > 0 && max <= blockMin) delta = Math.min(delta, blockMin - max);
                    else if (delta < 0 && min >= blockMin + 1.0) delta = Math.max(delta, blockMin + 1.0 - min);
                }
            }
        }
        return delta;
    }

    private static boolean solid(World world, int x, int y, int z) {
        return world.getBlock(new BlockPos(x, y, z)).orElseThrow() != BlockId.AIR;
    }

    private static Bounds bounds(Aabb region) {
        int minX = coordinate(Math.floor(region.minX())), maxX = coordinate(Math.ceil(region.maxX()) - 1);
        int minY = coordinate(Math.floor(region.minY())), maxY = coordinate(Math.ceil(region.maxY()) - 1);
        int minZ = coordinate(Math.floor(region.minZ())), maxZ = coordinate(Math.ceil(region.maxZ()) - 1);
        checkVolume(minX, minY, minZ, maxX, maxY, maxZ, MAX_BLOCKS);
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int coordinate(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Collision region is outside block coordinate range");
        }
        return (int) value;
    }

    private static void checkVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int limit) {
        double count = (maxX - (double) minX + 1) * (maxY - (double) minY + 1) * (maxZ - (double) minZ + 1);
        if (count > limit) throw new IllegalArgumentException("Collision region exceeds work budget");
    }

    // Preserve the stepped X/Z/Y solver as the CPU baseline for comparisons with swept clipping.
    Result moveStepped(World world, Aabb body, double x, double y, double z) {
        if (!available(world, requiredRegion(body, x, y, z)))
            return new Result(body, 0, 0, 0, false, false, false, false, false);
        Aabb moved = stepAxis(world, body, x, Axis.X);
        double allowedX = moved.minX() - body.minX();
        moved = stepAxis(world, moved, z, Axis.Z);
        double allowedZ = moved.minZ() - body.minZ();
        moved = stepAxis(world, moved, y, Axis.Y);
        double allowedY = moved.minY() - body.minY();
        return new Result(
                moved,
                allowedX,
                allowedY,
                allowedZ,
                Math.abs(allowedX - x) > 1e-9,
                Math.abs(allowedY - y) > 1e-9,
                Math.abs(allowedZ - z) > 1e-9,
                Math.abs(allowedY - y) > 1e-9 && y < 0,
                true);
    }

    private Aabb stepAxis(World world, Aabb body, double delta, Axis axis) {
        if (Math.abs(delta) <= 1e-9) return body;
        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta) / .25));
        for (int step = 0; step < steps; step++) {
            double part = delta / steps;
            Aabb target = offset(body, part, axis);
            if (spaceFree(world, target)) {
                body = target;
                continue;
            }
            double low = 0, high = 1;
            for (int iteration = 0; iteration < 24; iteration++) {
                double middle = (low + high) * .5;
                if (spaceFree(world, offset(body, part * middle, axis))) low = middle;
                else high = middle;
            }
            return offset(body, part * low, axis);
        }
        return body;
    }

    private static Aabb offset(Aabb body, double delta, Axis axis) {
        return switch (axis) {
            case X -> body.move(delta, 0, 0);
            case Y -> body.move(0, delta, 0);
            case Z -> body.move(0, 0, delta);
        };
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    public record Result(
            Aabb body,
            double x,
            double y,
            double z,
            boolean blockedX,
            boolean blockedY,
            boolean blockedZ,
            boolean grounded,
            boolean available) {}
}
