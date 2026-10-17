package com.norbertfila.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VoxelCollision {
    private static final double SUPPORT_AND_STEP_MARGIN = 0.6;
    private static final double EDGE_REDUCTION = 0.05;
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
                    if (solid(world, (int) x, (int) y, (int) z)
                            && body.intersects(shape(world, (int) x, (int) y, (int) z))) return false;
        return true;
    }

    public Result move(World world, Aabb body, double x, double y, double z) {
        return move(world, body, x, y, z, false);
    }

    public Result move(World world, Aabb body, double x, double y, double z, boolean protectEdge) {
        return move(world, body, x, y, z, protectEdge, false);
    }

    public Result move(World world, Aabb body, double x, double y, double z, boolean protectEdge, boolean allowStep) {
        Aabb region = requiredRegion(body, x, y, z);
        if (!available(world, region)) return new Result(body, 0, 0, 0, false, false, false, false, false);
        Bounds candidates = bounds(region);
        return move(
                body, x, y, z, protectEdge, allowStep, (box, delta, axis) -> clip(world, box, candidates, delta, axis));
    }

    public Result move(
            List<Aabb> obstacles, Aabb body, double x, double y, double z, boolean protectEdge, boolean allowStep) {
        bounds(requiredRegion(body, x, y, z));
        List<Aabb> shapes = List.copyOf(obstacles);
        return move(body, x, y, z, protectEdge, allowStep, (box, delta, axis) -> {
            for (Aabb shape : shapes)
                delta = clipAgainst(
                        box,
                        delta,
                        axis,
                        shape.minX(),
                        shape.minY(),
                        shape.minZ(),
                        shape.maxX(),
                        shape.maxY(),
                        shape.maxZ());
            return delta;
        });
    }

    private static Result move(
            Aabb body, double x, double y, double z, boolean protectEdge, boolean allowStep, Clipper clipper) {
        double requestedX = x, requestedZ = z;
        if (protectEdge && supported(clipper, body, 0, 0)) {
            while (x != 0 && !supported(clipper, body, x, 0)) x = towardZero(x);
            while (z != 0 && !supported(clipper, body, 0, z)) z = towardZero(z);
            while (x != 0 && z != 0 && !supported(clipper, body, x, z)) {
                x = towardZero(x);
                z = towardZero(z);
            }
        }
        double allowedY = clipper.clip(body, y, Axis.Y);
        Aabb moved = body.move(0, allowedY, 0);
        double allowedX = clipper.clip(moved, x, Axis.X);
        moved = moved.move(allowedX, 0, 0);
        double allowedZ = clipper.clip(moved, z, Axis.Z);
        moved = moved.move(0, 0, allowedZ);
        boolean grounded = allowedY != y && y < 0;
        if (allowStep && y < 0 && grounded && (allowedX != x || allowedZ != z)) {
            double up = clipper.clip(body, SUPPORT_AND_STEP_MARGIN, Axis.Y);
            Aabb raised = body.move(0, up, 0);
            double stepX = clipper.clip(raised, x, Axis.X);
            raised = raised.move(stepX, 0, 0);
            double stepZ = clipper.clip(raised, z, Axis.Z);
            raised = raised.move(0, 0, stepZ);
            double down = clipper.clip(raised, y - up, Axis.Y);
            // A horizontal gain is valid only if the downward sweep actually lands on a surface.
            if (down != y - up && Math.hypot(stepX, stepZ) > Math.hypot(allowedX, allowedZ)) {
                moved = raised.move(0, down, 0);
                allowedX = stepX;
                allowedY = up + down;
                allowedZ = stepZ;
            }
        }
        return new Result(
                moved,
                allowedX,
                allowedY,
                allowedZ,
                allowedX != requestedX,
                allowedY != y,
                allowedZ != requestedZ,
                grounded,
                true);
    }

    private static double towardZero(double delta) {
        return Math.abs(delta) <= EDGE_REDUCTION ? 0 : delta - Math.copySign(EDGE_REDUCTION, delta);
    }

    private static boolean supported(Clipper clipper, Aabb body, double x, double z) {
        return clipper.clip(body.move(x, 0, z), -SUPPORT_AND_STEP_MARGIN, Axis.Y) != -SUPPORT_AND_STEP_MARGIN;
    }

    private static double clip(World world, Aabb body, Bounds range, double delta, Axis axis) {
        if (delta == 0) return 0;
        for (long y = range.minY; y <= range.maxY; y++) {
            for (long z = range.minZ; z <= range.maxZ; z++) {
                for (long x = range.minX; x <= range.maxX; x++) {
                    if (!solid(world, (int) x, (int) y, (int) z)) continue;
                    var shape = shape(world, (int) x, (int) y, (int) z);
                    delta = clipAgainst(
                            body,
                            delta,
                            axis,
                            shape.minX(),
                            shape.minY(),
                            shape.minZ(),
                            shape.maxX(),
                            shape.maxY(),
                            shape.maxZ());
                }
            }
        }
        return delta;
    }

    private static Aabb shape(World world, int x, int y, int z) {
        var model = BlockRegistry.DEFAULT
                .definition((byte) world.getBlock(new BlockPos(x, y, z)).orElseThrow())
                .model();
        return new Aabb(
                x + model.minX(),
                y + model.minY(),
                z + model.minZ(),
                x + model.maxX(),
                y + model.maxY(),
                z + model.maxZ());
    }

    private static double clipAgainst(
            Aabb body,
            double delta,
            Axis axis,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        double min, max, obstacleMin, obstacleMax;
        boolean overlaps;
        switch (axis) {
            case X -> {
                min = body.minX();
                max = body.maxX();
                obstacleMin = minX;
                obstacleMax = maxX;
                overlaps = body.maxY() > minY && body.minY() < maxY && body.maxZ() > minZ && body.minZ() < maxZ;
            }
            case Y -> {
                min = body.minY();
                max = body.maxY();
                obstacleMin = minY;
                obstacleMax = maxY;
                overlaps = body.maxX() > minX && body.minX() < maxX && body.maxZ() > minZ && body.minZ() < maxZ;
            }
            case Z -> {
                min = body.minZ();
                max = body.maxZ();
                obstacleMin = minZ;
                obstacleMax = maxZ;
                overlaps = body.maxX() > minX && body.minX() < maxX && body.maxY() > minY && body.minY() < maxY;
            }
            default -> throw new AssertionError(axis);
        }
        if (overlaps) {
            if (delta > 0 && max <= obstacleMin) return Math.min(delta, obstacleMin - max);
            if (delta < 0 && min >= obstacleMax) return Math.max(delta, obstacleMax - min);
        }
        return delta;
    }

    private static boolean solid(World world, int x, int y, int z) {
        return BlockRegistry.DEFAULT.isCollidable(
                (byte) world.getBlock(new BlockPos(x, y, z)).orElseThrow());
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

    private interface Clipper {
        double clip(Aabb body, double delta, Axis axis);
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
