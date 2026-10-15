package com.norbertfila.render;

import com.norbertfila.streaming.RegionIndex;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RegionPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Hierarchical frustum culling over a {@link RegionIndex}. The frustum is camera-relative: bounds are moved by the
 * camera position in double precision before the float plane tests, so culling stays exact far from the origin.
 * Regions are visited nearest first, which gives opaque geometry an approximate front-to-back order.
 */
public final class RegionCuller {
    private long[] order = new long[64];
    private int regionTests, chunkTests, rejectedRegions, insideRegions;

    /** Appends the chunks whose bounds intersect {@code frustum} to {@code visible}, nearest regions first. */
    public void cull(
            Frustum frustum,
            RegionIndex index,
            double cameraX,
            double cameraY,
            double cameraZ,
            List<ChunkPos> visible) {
        Objects.requireNonNull(frustum);
        Objects.requireNonNull(visible);
        regionTests = chunkTests = rejectedRegions = insideRegions = 0;
        int regions = index.regionCount();
        if (order.length < regions) order = new long[Math.max(regions, order.length * 2)];
        for (int slot = 0; slot < regions; slot++) {
            RegionPos.Bounds bounds = index.region(slot).bounds();
            double dx = (bounds.minX() + bounds.maxExclusiveX()) * .5 * Chunk.SIZE - cameraX;
            double dy = (bounds.minY() + bounds.maxExclusiveY()) * .5 * Chunk.SIZE - cameraY;
            double dz = (bounds.minZ() + bounds.maxExclusiveZ()) * .5 * Chunk.SIZE - cameraZ;
            // Non-negative float bits sort like the floats themselves, so one long holds distance and slot.
            order[slot] = (long) Float.floatToIntBits((float) (dx * dx + dy * dy + dz * dz)) << 32 | slot;
        }
        Arrays.sort(order, 0, regions);
        for (int rank = 0; rank < regions; rank++) {
            RegionIndex.Region region = index.region((int) order[rank]);
            RegionPos.Bounds bounds = region.bounds();
            regionTests++;
            int classification = classify(
                    frustum,
                    bounds.minX(),
                    bounds.minY(),
                    bounds.minZ(),
                    bounds.maxExclusiveX(),
                    bounds.maxExclusiveY(),
                    bounds.maxExclusiveZ(),
                    cameraX,
                    cameraY,
                    cameraZ);
            if (classification == Frustum.OUTSIDE) {
                rejectedRegions++;
                continue;
            }
            if (classification == Frustum.INSIDE) insideRegions++;
            for (int chunk = 0; chunk < region.size(); chunk++) {
                ChunkPos position = region.chunk(chunk);
                if (classification == Frustum.INSIDE || intersects(frustum, position, cameraX, cameraY, cameraZ)) {
                    visible.add(position);
                }
                if (classification != Frustum.INSIDE) chunkTests++;
            }
        }
    }

    /** Reference result: tests every chunk on its own, without regions or ordering. */
    public static List<ChunkPos> simpleVisible(
            Frustum frustum, RegionIndex index, double cameraX, double cameraY, double cameraZ) {
        List<ChunkPos> visible = new ArrayList<>();
        for (RegionPos region : index.regions())
            for (ChunkPos chunk : index.chunks(region))
                if (intersects(frustum, chunk, cameraX, cameraY, cameraZ)) visible.add(chunk);
        return visible;
    }

    private static boolean intersects(Frustum frustum, ChunkPos chunk, double x, double y, double z) {
        return classify(
                        frustum,
                        chunk.x(),
                        chunk.y(),
                        chunk.z(),
                        chunk.x() + 1L,
                        chunk.y() + 1L,
                        chunk.z() + 1L,
                        x,
                        y,
                        z)
                != Frustum.OUTSIDE;
    }

    /** Classifies chunk-unit bounds after translating them to camera-relative block coordinates. */
    private static int classify(
            Frustum frustum,
            long minX,
            long minY,
            long minZ,
            long maxX,
            long maxY,
            long maxZ,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return frustum.classify(
                (float) (minX * Chunk.SIZE - cameraX),
                (float) (minY * Chunk.SIZE - cameraY),
                (float) (minZ * Chunk.SIZE - cameraZ),
                (float) (maxX * Chunk.SIZE - cameraX),
                (float) (maxY * Chunk.SIZE - cameraY),
                (float) (maxZ * Chunk.SIZE - cameraZ));
    }

    public int regionTests() {
        return regionTests;
    }

    public int chunkTests() {
        return chunkTests;
    }

    public int rejectedRegions() {
        return rejectedRegions;
    }

    public int insideRegions() {
        return insideRegions;
    }
}
