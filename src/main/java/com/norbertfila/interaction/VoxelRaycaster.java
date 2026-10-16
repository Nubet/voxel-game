package com.norbertfila.interaction;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

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
        return cast(
                world,
                originX,
                originY,
                originZ,
                directionX,
                directionY,
                directionZ,
                maxDistance,
                id -> BlockRegistry.DEFAULT.definition((byte) id).targetable());
    }

    public Optional<Hit> cast(
            World world,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            double maxDistance,
            IntPredicate targetable) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(targetable);
        validate(originX, originY, originZ, directionX, directionY, directionZ, maxDistance);
        double scale = Math.max(Math.abs(directionX), Math.max(Math.abs(directionY), Math.abs(directionZ)));
        if (scale == 0) throw new IllegalArgumentException("Direction must not be zero");
        directionX /= scale;
        directionY /= scale;
        directionZ /= scale;
        double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        directionX /= length;
        directionY /= length;
        directionZ /= length;

        int x = floorToInt(originX);
        int y = floorToInt(originY);
        int z = floorToInt(originZ);
        BlockPos position = new BlockPos(x, y, z);
        int blockId = blockId(world, position);
        if (blockId < 0) return Optional.empty();
        if (blockId != BlockId.AIR && targetable.test(blockId)) {
            Hit hit = modelHit(
                    position,
                    blockId,
                    originX,
                    originY,
                    originZ,
                    directionX,
                    directionY,
                    directionZ,
                    0,
                    maxDistance,
                    Normal.ZERO);
            if (hit != null) return Optional.of(hit);
        }

        int stepX = step(directionX);
        int stepY = step(directionY);
        int stepZ = step(directionZ);
        double tMaxX = firstBoundary(originX, x, directionX, stepX);
        double tMaxY = firstBoundary(originY, y, directionY, stepY);
        double tMaxZ = firstBoundary(originZ, z, directionZ, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(directionX);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(directionY);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(directionZ);

        while (true) {
            double distance;
            Normal normal;
            // Resolve equal boundary distances in X/Y/Z order to keep edge and corner hits deterministic.
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > maxDistance || stepX == 0 || x == boundary(stepX)) return Optional.empty();
                x += stepX;
                distance = tMaxX;
                tMaxX += tDeltaX;
                normal = new Normal(-stepX, 0, 0);
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > maxDistance || stepY == 0 || y == boundary(stepY)) return Optional.empty();
                y += stepY;
                distance = tMaxY;
                tMaxY += tDeltaY;
                normal = new Normal(0, -stepY, 0);
            } else {
                if (tMaxZ > maxDistance || stepZ == 0 || z == boundary(stepZ)) return Optional.empty();
                z += stepZ;
                distance = tMaxZ;
                tMaxZ += tDeltaZ;
                normal = new Normal(0, 0, -stepZ);
            }
            position = new BlockPos(x, y, z);
            blockId = blockId(world, position);
            if (blockId < 0) return Optional.empty();
            if (blockId != BlockId.AIR && targetable.test(blockId)) {
                Hit hit = modelHit(
                        position,
                        blockId,
                        originX,
                        originY,
                        originZ,
                        directionX,
                        directionY,
                        directionZ,
                        distance,
                        maxDistance,
                        normal);
                if (hit != null) return Optional.of(hit);
            }
        }
    }

    private static Hit modelHit(
            BlockPos position,
            int block,
            double ox,
            double oy,
            double oz,
            double dx,
            double dy,
            double dz,
            double entry,
            double limit,
            Normal normal) {
        var model = BlockRegistry.DEFAULT.definition((byte) block).model();
        double localY = oy + dy * entry - position.y();
        if (localY >= model.minY() && localY <= model.maxY()) return new Hit(position, normal, entry, block);
        if (dy == 0) return null;
        double distance = (position.y() + (dy < 0 ? model.maxY() : model.minY()) - oy) / dy;
        double x = ox + dx * distance - position.x(), z = oz + dz * distance - position.z();
        return distance >= entry && distance <= limit && x >= 0 && x <= 1 && z >= 0 && z <= 1
                ? new Hit(position, new Normal(0, dy < 0 ? 1 : -1, 0), distance, block)
                : null;
    }

    private static double firstBoundary(double origin, int cell, double direction, int step) {
        if (step > 0) return (cell + 1.0 - origin) / direction;
        if (step < 0) return (origin - cell) / -direction;
        return Double.POSITIVE_INFINITY;
    }

    private static int boundary(int step) {
        return step > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    }

    private static int step(double direction) {
        return direction > 0 ? 1 : direction < 0 ? -1 : 0;
    }

    private static int blockId(World world, BlockPos position) {
        if (!world.hasChunk(Coordinates.worldToChunk(position))) return -1;
        return world.getBlock(position).orElse(-1);
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
