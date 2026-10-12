package com.norbertfila.generation;

import com.norbertfila.world.Chunk;
import java.util.Objects;

public record TerrainConfig(
        int baseHeight,
        Layer continental,
        Layer hills,
        Layer details,
        Mountains mountains,
        Layer warp,
        int waterLevel) {
    public TerrainConfig {
        Objects.requireNonNull(continental);
        Objects.requireNonNull(hills);
        Objects.requireNonNull(details);
        Objects.requireNonNull(mountains);
        Objects.requireNonNull(warp);
        if (waterLevel < Integer.MIN_VALUE + Chunk.SIZE || waterLevel > Integer.MAX_VALUE - 2 * Chunk.SIZE)
            throw new IllegalArgumentException("Water level exceeds supported world coordinates");
        double amplitude = continental.amplitude() + hills.amplitude() + details.amplitude();
        if (baseHeight - amplitude < Integer.MIN_VALUE + 2.0 * Chunk.SIZE
                || baseHeight + amplitude + mountains.ridge().amplitude() > Integer.MAX_VALUE - 2.0 * Chunk.SIZE)
            throw new IllegalArgumentException("Terrain height range exceeds supported world coordinates");
        double coordinateBound = 0x1p31 + warp.amplitude();
        for (Layer layer : new Layer[] {
            continental, hills, details, mountains.region(), mountains.ridge(), mountains.ranges(), warp
        }) {
            double frequency = FractalNoise.validateParameters(
                    layer.frequency(), layer.octaves(), layer.persistence(), layer.lacunarity());
            if (!Double.isFinite(coordinateBound) || frequency * coordinateBound + 256 >= 0x1p52)
                throw new IllegalArgumentException("Warped noise coordinates lose fractional precision");
        }
    }

    public TerrainConfig(
            int baseHeight, Layer continental, Layer hills, Layer details, Mountains mountains, Layer warp) {
        this(baseHeight, continental, hills, details, mountains, warp, (int)
                Math.max(Integer.MIN_VALUE, (long) baseHeight - 4));
    }

    public TerrainConfig(int baseHeight, Layer continental, Layer hills, Layer details, Mountains mountains) {
        this(baseHeight, continental, hills, details, mountains, new Layer(.003, 3, .5, 2, 0));
    }

    public TerrainConfig(int baseHeight, Layer continental, Layer hills, Layer details) {
        this(
                baseHeight,
                continental,
                hills,
                details,
                new Mountains(new Layer(.0008, 2, .5, 2, 1), new Layer(.007, 4, .5, 2, 0), .56, .66));
    }

    public static TerrainConfig defaults() {
        return new TerrainConfig(
                32,
                new Layer(.0013, 4, .5, 2, 35),
                new Layer(.012, 3, .5, 2, 12),
                new Layer(.06, 3, .5, 2, 3),
                new Mountains(
                        new Layer(.0008, 2, .5, 2, 1),
                        new Layer(.005, 5, .5, 2, 218),
                        .54,
                        .62,
                        new Layer(.0018, 3, .5, 2, 1),
                        3,
                        1.25,
                        true),
                new Layer(.003, 3, .5, 2, 140));
    }

    public static TerrainConfig classic() {
        return new TerrainConfig(
                32,
                new Layer(.002, 4, .5, 2, 25),
                new Layer(.012, 3, .5, 2, 12),
                new Layer(.06, 2, .5, 2, 3),
                new Mountains(new Layer(.0008, 2, .5, 2, 1), new Layer(.007, 4, .5, 2, 75), .56, .66));
    }

    public TerrainConfig withoutWarp() {
        return new TerrainConfig(
                baseHeight,
                continental,
                hills,
                details,
                mountains,
                new Layer(warp.frequency(), warp.octaves(), warp.persistence(), warp.lacunarity(), 0),
                waterLevel);
    }

    public TerrainConfig withoutMountains() {
        Layer ridge = mountains.ridge();
        return new TerrainConfig(
                baseHeight,
                continental,
                hills,
                details,
                new Mountains(
                        mountains.region(),
                        new Layer(ridge.frequency(), ridge.octaves(), ridge.persistence(), ridge.lacunarity(), 0),
                        mountains.maskStart(),
                        mountains.maskEnd(),
                        mountains.ranges(),
                        mountains.ridgeExponent(),
                        mountains.rangeExponent(),
                        mountains.perOctaveRidges()),
                warp,
                waterLevel);
    }

    public int minHeight() {
        return (int) Math.floor(baseHeight - continental.amplitude() - hills.amplitude() - details.amplitude());
    }

    public int maxHeight() {
        return (int) Math.ceil(baseHeight
                + continental.amplitude()
                + hills.amplitude()
                + details.amplitude()
                + mountains.ridge().amplitude());
    }

    public record Mountains(
            Layer region,
            Layer ridge,
            double maskStart,
            double maskEnd,
            Layer ranges,
            double ridgeExponent,
            double rangeExponent,
            boolean perOctaveRidges) {
        public Mountains {
            Objects.requireNonNull(region);
            Objects.requireNonNull(ridge);
            Objects.requireNonNull(ranges);
            if (!Double.isFinite(maskStart)
                    || !Double.isFinite(maskEnd)
                    || maskStart < 0
                    || maskStart >= maskEnd
                    || maskEnd > 1)
                throw new IllegalArgumentException("Mountain mask thresholds must satisfy 0 <= start < end <= 1");
            if (!Double.isFinite(ridgeExponent)
                    || ridgeExponent <= 0
                    || !Double.isFinite(rangeExponent)
                    || rangeExponent <= 0)
                throw new IllegalArgumentException("Mountain shaping exponents must be finite and positive");
        }

        public Mountains(Layer region, Layer ridge, double maskStart, double maskEnd) {
            this(region, ridge, maskStart, maskEnd, new Layer(.0018, 1, .5, 2, 0), 2, 1, false);
        }
    }

    public record Layer(double frequency, int octaves, double persistence, double lacunarity, double amplitude) {
        public Layer {
            double maximumFrequency = FractalNoise.validateParameters(frequency, octaves, persistence, lacunarity);
            if (maximumFrequency * 0x1p31 >= 0x1p52)
                throw new IllegalArgumentException("Noise frequency loses fractional precision within the world");
            if (!Double.isFinite(amplitude) || amplitude < 0)
                throw new IllegalArgumentException("Terrain amplitude must be finite and nonnegative");
        }

        Noise2D fractal(Noise2D noise) {
            return new FractalNoise(noise, frequency, octaves, persistence, lacunarity);
        }
    }
}
