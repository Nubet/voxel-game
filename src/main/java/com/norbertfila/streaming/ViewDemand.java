package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private List<ChunkPos> requested = List.of();

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
        if (changed) center = null;
        return changed;
    }

    public boolean update(ChunkPos newCenter) {
        Objects.requireNonNull(newCenter);
        if (newCenter.equals(center)) return false;
        center = newCenter;
        List<ChunkPos> next = new ArrayList<>();
        for (int y = minChunkY; y <= maxChunkY; y++) {
            for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                    if (shape == Shape.CIRCLE && x * x + z * z > horizontalRadius * horizontalRadius) continue;
                    next.add(new ChunkPos(newCenter.x() + x, y, newCenter.z() + z));
                }
            }
        }
        requested = List.copyOf(next);
        return true;
    }

    public ChunkPos center() {
        return center;
    }

    public List<ChunkPos> requested() {
        return requested;
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
