package com.norbertfila.render;

import com.norbertfila.world.Chunk;

public record DistanceFog(float start, float end) {
    public DistanceFog {
        if (!Float.isFinite(start) || !Float.isFinite(end) || start < 0 || end <= start)
            throw new IllegalArgumentException("Fog range must be finite with 0 <= start < end");
    }

    public static DistanceFog forDistances(int detailChunks, int landscapeChunks) {
        if (detailChunks < 1 || landscapeChunks < 0)
            throw new IllegalArgumentException("View distances must be positive, or zero for disabled landscape");
        float end = (float) Math.max(detailChunks, landscapeChunks) * Chunk.SIZE;
        return new DistanceFog(end * .65f, end);
    }
}
