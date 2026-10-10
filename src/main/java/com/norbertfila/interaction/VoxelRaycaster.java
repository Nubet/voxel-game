package com.norbertfila.interaction;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.World;
import java.util.Objects;
import java.util.Optional;

public final class VoxelRaycaster {
    public Optional<Hit> cast(
            World world,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            double maxDistance) {
        Objects.requireNonNull(world);
        validate(originX, originY, originZ, directionX, directionY, directionZ, maxDistance);
        double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        if (length == 0) throw new IllegalArgumentException("Direction must not be zero");
        directionX /= length;
        directionY /= length;
        directionZ /= length;

        int x = floorToInt(originX);
        int y = floorToInt(originY);
        int z = floorToInt(originZ);
        BlockPos position = new BlockPos(x, y, z);
        if (solid(world, position)) return Optional.of(new Hit(position, Normal.ZERO, 0, blockId(world, position)));

        int stepX = Double.compare(directionX, 0);
        int stepY = Double.compare(directionY, 0);
        int stepZ = Double.compare(directionZ, 0);
        double tMaxX = firstBoundary(originX, x, directionX, stepX);
        double tMaxY = firstBoundary(originY, y, directionY, stepY);
        double tMaxZ = firstBoundary(originZ, z, directionZ, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(directionX);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(directionY);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(directionZ);

        while (true) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > maxDistance || stepX == 0 || x == boundary(stepX)) return Optional.empty();
                x += stepX;
                double distance = tMaxX;
                tMaxX += tDeltaX;
                position = new BlockPos(x, y, z);
                if (solid(world, position))
                    return Optional.of(new Hit(position, new Normal(-stepX, 0, 0), distance, blockId(world, position)));
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > maxDistance || stepY == 0 || y == boundary(stepY)) return Optional.empty();
                y += stepY;
                double distance = tMaxY;
                tMaxY += tDeltaY;
                position = new BlockPos(x, y, z);
                if (solid(world, position))
                    return Optional.of(new Hit(position, new Normal(0, -stepY, 0), distance, blockId(world, position)));
            } else {
                if (tMaxZ > maxDistance || stepZ == 0 || z == boundary(stepZ)) return Optional.empty();
                z += stepZ;
                double distance = tMaxZ;
                tMaxZ += tDeltaZ;
                position = new BlockPos(x, y, z);
                if (solid(world, position))
                    return Optional.of(new Hit(position, new Normal(0, 0, -stepZ), distance, blockId(world, position)));
            }
        }
    }

    private static double firstBoundary(double origin, int cell, double direction, int step) {
        if (step > 0) return (cell + 1.0 - origin) / direction;
        if (step < 0) return (origin - cell) / -direction;
        return Double.POSITIVE_INFINITY;
    }

    private static int boundary(int step) {
        return step > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    }

    private static boolean solid(World world, BlockPos position) {
        return blockId(world, position) != BlockId.AIR;
    }

    private static int blockId(World world, BlockPos position) {
        return world.getBlock(position).orElse(BlockId.AIR & 0xFF);
    }

    private static int floorToInt(double value) {
        double floor = Math.floor(value);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Origin is outside block coordinate range");
        }
        return (int) floor;
    }

    private static void validate(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
        if (!finite(ox) || !finite(oy) || !finite(oz) || !finite(dx) || !finite(dy) || !finite(dz)) {
            throw new IllegalArgumentException("Ray values must be finite");
        }
        if (!finite(maxDistance) || maxDistance < 0)
            throw new IllegalArgumentException("Max distance must be finite and non-negative");
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    public record Normal(int x, int y, int z) {
        public static final Normal ZERO = new Normal(0, 0, 0);
    }

    public record Hit(BlockPos block, Normal normal, double distance, int blockId) {
        public Hit {
            Objects.requireNonNull(block);
            Objects.requireNonNull(normal);
            if (distance < 0) throw new IllegalArgumentException("Hit distance must be non-negative");
        }
    }
}
