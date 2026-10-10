package com.norbertfila.generation;

@FunctionalInterface
public interface Noise2D {
    double sample(double x, double z);
}
