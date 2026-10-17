package com.norbertfila.core;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.*;

public record CameraEnvironment(ChunkPos center, BlockNeighborhood blocks) {
    private static final double BOOM = 4;
    private static final double NEAR = .1;

    public CameraEnvironment {
        java.util.Objects.requireNonNull(center);
    }

    public void position(Camera camera, PlayerState.Position eye, int width, int height) {
        boolean front = camera.perspective() == Camera.Perspective.FRONT;
        camera.renderLook(front ? camera.yaw() + 180 : camera.yaw(), front ? -camera.pitch() : camera.pitch());
        double distance = camera.perspective() == Camera.Perspective.FIRST ? 0 : BOOM;
        double yaw = Math.toRadians(camera.yaw()), pitch = Math.toRadians(camera.pitch());
        double sign = front ? 1 : -1;
        double dx = sign * Math.cos(yaw) * Math.cos(pitch) * distance;
        double dy = sign * Math.sin(pitch) * distance;
        double dz = sign * Math.sin(yaw) * Math.cos(pitch) * distance;
        double tangent = Math.tan(Math.toRadians(camera.fieldOfView() * .5));
        double aspect = (double) width / Math.max(1, height);
        // The sphere encloses every near-plane corner at any camera rotation.
        double radius = NEAR * Math.sqrt(1 + tangent * tangent * (1 + aspect * aspect)) + .02;
        double fraction = distance == 0 ? 0 : clip(eye, dx, dy, dz, radius);
        camera.setPosition(eye.x() + dx * fraction, eye.y() + dy * fraction, eye.z() + dz * fraction);
    }

    double clip(PlayerState.Position eye, double dx, double dy, double dz, double radius) {
        if (blocks == null) return 0;
        double x = eye.x() - (double) center.x() * Chunk.SIZE;
        double y = eye.y() - (double) center.y() * Chunk.SIZE;
        double z = eye.z() - (double) center.z() * Chunk.SIZE;
        if (x - radius < -Chunk.SIZE
                || y - radius < -Chunk.SIZE
                || z - radius < -Chunk.SIZE
                || x + radius >= 2 * Chunk.SIZE
                || y + radius >= 2 * Chunk.SIZE
                || z + radius >= 2 * Chunk.SIZE) return 0;
        double fraction = 1;
        for (int bx = (int) Math.floor(Math.min(x, x + dx) - radius);
                bx <= Math.floor(Math.max(x, x + dx) + radius);
                bx++)
            for (int by = (int) Math.floor(Math.min(y, y + dy) - radius);
                    by <= Math.floor(Math.max(y, y + dy) + radius);
                    by++)
                for (int bz = (int) Math.floor(Math.min(z, z + dz) - radius);
                        bz <= Math.floor(Math.max(z, z + dz) + radius);
                        bz++) {
                    boolean unknown = blocks.chunkAt(bx, by, bz) == null;
                    var definition = BlockRegistry.DEFAULT.definition(blocks.block(bx, by, bz));
                    if (!unknown && !definition.collidable()) continue;
                    var model = unknown ? BlockModel.full(BlockShape.CUBE) : definition.model();
                    fraction = Math.min(
                            fraction,
                            intersection(
                                    x,
                                    y,
                                    z,
                                    dx,
                                    dy,
                                    dz,
                                    bx + model.minX() - radius,
                                    by + model.minY() - radius,
                                    bz + model.minZ() - radius,
                                    bx + model.maxX() + radius,
                                    by + model.maxY() + radius,
                                    bz + model.maxZ() + radius));
                }
        return fraction == 1 ? 1 : Math.max(0, fraction - .001 / BOOM);
    }

    private static double intersection(
            double x,
            double y,
            double z,
            double dx,
            double dy,
            double dz,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        double enter = 0, exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            double start = axis == 0 ? x : axis == 1 ? y : z;
            double delta = axis == 0 ? dx : axis == 1 ? dy : dz;
            double min = axis == 0 ? minX : axis == 1 ? minY : minZ;
            double max = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            if (delta == 0) {
                if (start < min || start > max) return 1;
            } else {
                double a = (min - start) / delta, b = (max - start) / delta;
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (enter > exit) return 1;
            }
        }
        return enter;
    }

    public Medium medium(double worldX, double worldY, double worldZ) {
        if (blocks == null) return new Medium(false, false);
        double x = worldX - (double) center.x() * Chunk.SIZE;
        double y = worldY - (double) center.y() * Chunk.SIZE;
        double z = worldZ - (double) center.z() * Chunk.SIZE;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        int sample = blocks.waterSample(bx, by, bz);
        if (sample == WaterSurface.UNKNOWN) return new Medium(false, false);
        if (sample < WaterState.SOURCE) return new Medium(false, true);
        boolean available = true;
        for (int sx = bx - 1; sx <= bx + 1; sx++)
            for (int sy = by; sy <= by + 1; sy++)
                for (int sz = bz - 1; sz <= bz + 1; sz++)
                    available &= blocks.waterSample(sx, sy, sz) != WaterSurface.UNKNOWN;
        var corners = WaterSurface.corners(blocks::waterSample, bx, by, bz);
        return new Medium(y - by < corners.heightAt(x - bx, z - bz), available);
    }

    public record Medium(boolean submerged, boolean available) {}
}
