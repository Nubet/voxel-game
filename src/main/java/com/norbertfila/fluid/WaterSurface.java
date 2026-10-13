package com.norbertfila.fluid;

import com.norbertfila.world.WaterState;

public final class WaterSurface {
    public static final int EMPTY = WaterState.EMPTY;
    public static final int SOLID = -2;
    public static final int UNKNOWN = -3;

    private WaterSurface() {}

    @FunctionalInterface
    public interface Samples {
        int at(int x, int y, int z);
    }

    public static float height(int state) {
        if (state < WaterState.SOURCE || state > WaterState.FALLING)
            throw new IllegalArgumentException("Not a water state");
        return (8 - WaterState.distance((byte) state)) / 9f;
    }

    public static Corners corners(Samples samples, int x, int y, int z) {
        return new Corners(
                corner(samples, x, y, z),
                corner(samples, x + 1, y, z),
                corner(samples, x + 1, y, z + 1),
                corner(samples, x, y, z + 1));
    }

    private static float corner(Samples samples, int x, int y, int z) {
        float total = 0;
        int weight = 0;
        for (int dx = -1; dx <= 0; dx++)
            for (int dz = -1; dz <= 0; dz++) {
                int state = samples.at(x + dx, y, z + dz);
                if (state == SOLID || state == UNKNOWN) continue;
                if (state >= WaterState.SOURCE) {
                    if (samples.at(x + dx, y + 1, z + dz) >= WaterState.SOURCE) return 1;
                    int contribution = state == WaterState.SOURCE || state == WaterState.FALLING ? 10 : 1;
                    total += height(state) * contribution;
                    weight += contribution;
                } else weight++;
            }
        return weight == 0 ? 0 : total / weight;
    }

    public record Corners(float h00, float h10, float h11, float h01) {
        public float at(int x, int z) {
            return x == 0 ? (z == 0 ? h00 : h01) : (z == 0 ? h10 : h11);
        }

        public double heightAt(double x, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(z) || x < 0 || x > 1 || z < 0 || z > 1)
                throw new IllegalArgumentException("Surface coordinates must be in [0,1]");
            // Match the top mesh's h01-to-h10 diagonal, rather than bilinear interpolation.
            return x + z <= 1
                    ? h00 + x * (h10 - h00) + z * (h01 - h00)
                    : h11 + (1 - x) * (h01 - h11) + (1 - z) * (h10 - h11);
        }
    }
}
