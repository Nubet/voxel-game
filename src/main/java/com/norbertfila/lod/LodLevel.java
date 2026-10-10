package com.norbertfila.lod;

public record LodLevel(int minRadiusChunks, int maxRadiusChunks, int sampleStep) {
    public LodLevel {
        if (minRadiusChunks < 0 || maxRadiusChunks < minRadiusChunks)
            throw new IllegalArgumentException("Invalid LOD radius");
        if (sampleStep < 1) throw new IllegalArgumentException("Sample step must be positive");
    }

    public boolean contains(long distanceSquared) {
        long min = (long) minRadiusChunks * minRadiusChunks;
        long max = (long) maxRadiusChunks * maxRadiusChunks;
        return distanceSquared >= min && distanceSquared <= max;
    }
}
