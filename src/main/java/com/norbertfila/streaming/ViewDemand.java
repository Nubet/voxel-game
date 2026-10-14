package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The set of chunk sections the view needs: every column within the horizontal radius of the center, for every
 * section between {@code minChunkY} and {@code maxChunkY}. Membership is arithmetic, and {@link #update} reports only
 * the sections that entered or left, found by scanning columns rather than sections.
 */
public final class ViewDemand {
    public enum Shape {
        SQUARE,
        CIRCLE
    }

    private int horizontalRadius;
    private int minChunkY;
    private int maxChunkY;
    private Shape shape;
    private ChunkPos center;
    private List<ChunkPos> requested;
    private List<ChunkPos> added = List.of();
    private List<ChunkPos> removed = List.of();

    public ViewDemand(int horizontalRadius, int minChunkY, int maxChunkY, Shape shape) {
        configure(horizontalRadius, minChunkY, maxChunkY, shape);
    }

    public boolean configure(int horizontalRadius, int minChunkY, int maxChunkY, Shape shape) {
        if (horizontalRadius < 0) throw new IllegalArgumentException("Horizontal radius must not be negative");
        if (minChunkY > maxChunkY)
            throw new IllegalArgumentException("Minimum chunk Y must not exceed maximum chunk Y");
        Objects.requireNonNull(shape);
        boolean changed = this.horizontalRadius != horizontalRadius
                || this.minChunkY != minChunkY
                || this.maxChunkY != maxChunkY
                || this.shape != shape;
        this.horizontalRadius = horizontalRadius;
        this.minChunkY = minChunkY;
        this.maxChunkY = maxChunkY;
        this.shape = shape;
        if (changed) {
            center = null;
            requested = null;
        }
        return changed;
    }

    /**
     * Moves the demand to {@code newCenter}. Returns false when the center is unchanged. After a configuration change
     * the previous demand is unknown, so {@link #added()} holds the whole demand and {@link #removed()} is empty.
     */
    public boolean update(ChunkPos newCenter) {
        Objects.requireNonNull(newCenter);
        if (newCenter.equals(center)) return false;
        ChunkPos previous = center;
        center = newCenter;
        requested = null;
        if (previous == null) {
            added = requested();
            removed = List.of();
        } else {
            added = difference(newCenter, previous);
            removed = difference(previous, newCenter);
        }
        return true;
    }

    /** Sections demanded around {@code inside} but not around {@code outside}, nearest to {@code inside} first. */
    private List<ChunkPos> difference(ChunkPos inside, ChunkPos outside) {
        List<ChunkPos> result = new ArrayList<>();
        for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++)
            for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
                long x = (long) inside.x() + dx, z = (long) inside.z() + dz;
                if (!inShape(dx, dz) || inShape(x - outside.x(), z - outside.z())) continue;
                for (int y = minChunkY; y <= maxChunkY; y++) result.add(new ChunkPos((int) x, y, (int) z));
            }
        result.sort(Comparator.comparingLong(position -> ChunkPriority.distanceSquared(position, inside)));
        return List.copyOf(result);
    }

    public boolean contains(ChunkPos position) {
        return center != null
                && position.y() >= minChunkY
                && position.y() <= maxChunkY
                && inShape((long) position.x() - center.x(), (long) position.z() - center.z());
    }

    private boolean inShape(long dx, long dz) {
        return shape == Shape.SQUARE
                ? Math.abs(dx) <= horizontalRadius && Math.abs(dz) <= horizontalRadius
                : dx * dx + dz * dz <= (long) horizontalRadius * horizontalRadius;
    }

    public ChunkPos center() {
        return center;
    }

    /** Sections that entered the demand in the last {@link #update}. */
    public List<ChunkPos> added() {
        return added;
    }

    /** Sections that left the demand in the last {@link #update}. */
    public List<ChunkPos> removed() {
        return removed;
    }

    /** The whole demand, nearest first. Built on request because streaming only needs the differences. */
    public List<ChunkPos> requested() {
        if (center == null) return List.of();
        if (requested == null) {
            List<ChunkPos> all = new ArrayList<>();
            for (int y = minChunkY; y <= maxChunkY; y++)
                for (int z = -horizontalRadius; z <= horizontalRadius; z++)
                    for (int x = -horizontalRadius; x <= horizontalRadius; x++)
                        if (inShape(x, z)) all.add(new ChunkPos(center.x() + x, y, center.z() + z));
            all.sort(Comparator.comparingLong(position -> ChunkPriority.distanceSquared(position, center)));
            requested = List.copyOf(all);
        }
        return requested;
    }

    /** Number of demanded sections, computed without building the list. */
    public int size() {
        int columns = 0;
        for (int z = -horizontalRadius; z <= horizontalRadius; z++)
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) if (inShape(x, z)) columns++;
        return center == null ? 0 : columns * (maxChunkY - minChunkY + 1);
    }

    public int horizontalRadius() {
        return horizontalRadius;
    }

    public int minChunkY() {
        return minChunkY;
    }

    public int maxChunkY() {
        return maxChunkY;
    }

    public Shape shape() {
        return shape;
    }
}
