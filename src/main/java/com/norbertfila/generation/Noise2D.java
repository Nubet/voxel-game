package com.norbertfila.generation;

@FunctionalInterface
public interface Noise2D {
    /** Terrain height bounds require samples in [-1, 1]. */
    double sample(double x, double z);
}
