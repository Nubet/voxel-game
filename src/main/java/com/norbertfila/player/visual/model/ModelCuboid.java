package com.norbertfila.player.visual.model;

public record ModelCuboid(float x, float y, float z, float width, float height, float depth) {
    public ModelCuboid {
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(z)
                || !Float.isFinite(width)
                || !Float.isFinite(height)
                || !Float.isFinite(depth)
                || width <= 0
                || height <= 0
                || depth <= 0)
            throw new IllegalArgumentException("Cuboid must have finite coordinates and positive dimensions");
    }
}
