package com.norbertfila.generation;

@FunctionalInterface
interface GroundSampler {
    TerrainSummary sample(int x, int z);

    default int surfaceHeight(int x, int z) {
        return sample(x, z).surfaceHeight();
    }
}
