package com.norbertfila.generation;

/** Sampling runs concurrently with chunk generation and Far LOD; implementations must not access live world state. */
public interface TerrainSource extends WorldGenerator {
    int surfaceHeight(int worldX, int worldZ);

    TerrainSummary sample(int worldX, int worldZ);

    /** Selects a representative column inside the square; tile interiors and borders must use the same selection. */
    default SurfaceSample sampleArea(int originX, int originZ, int size) {
        if (size < 1 || size > 64) throw new IllegalArgumentException("Invalid terrain sample size");
        Math.addExact(originX, size - 1);
        Math.addExact(originZ, size - 1);
        int x = originX + size / 2, z = originZ + size / 2;
        return new SurfaceSample(x, z, sample(x, z), false);
    }

    record SurfaceSample(int x, int z, TerrainSummary terrain, boolean detailedWalls) {}

    default java.util.List<Canopy> canopies(int originX, int originZ, int size) {
        return java.util.List.of();
    }

    default int tintIndex(int worldX, int worldZ) {
        return 0;
    }
}
