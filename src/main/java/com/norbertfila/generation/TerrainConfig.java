package com.norbertfila.generation;

import com.norbertfila.world.Chunk;
import java.util.Objects;

public record TerrainConfig(int baseHeight, Layer continental, Layer hills, Layer details, Mountains mountains) {
    public TerrainConfig {
        Objects.requireNonNull(continental);
        Objects.requireNonNull(hills);
        Objects.requireNonNull(details);
        Objects.requireNonNull(mountains);
        double amplitude = continental.amplitude() + hills.amplitude() + details.amplitude();
        if (baseHeight - amplitude < Integer.MIN_VALUE + 2.0 * Chunk.SIZE
                || baseHeight + amplitude + mountains.ridge().amplitude() > Integer.MAX_VALUE - 2.0 * Chunk.SIZE)
            throw new IllegalArgumentException("Terrain height range exceeds supported world coordinates");
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
                new Layer(.002, 4, .5, 2, 25),
                new Layer(.012, 3, .5, 2, 12),
                new Layer(.06, 2, .5, 2, 3),
                new Mountains(new Layer(.0008, 2, .5, 2, 1), new Layer(.007, 4, .5, 2, 75), .56, .66));
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

    public record Mountains(Layer region, Layer ridge, double maskStart, double maskEnd) {
        public Mountains {
            Objects.requireNonNull(region);
            Objects.requireNonNull(ridge);
            if (!Double.isFinite(maskStart)
                    || !Double.isFinite(maskEnd)
                    || maskStart < 0
                    || maskStart >= maskEnd
                    || maskEnd > 1)
                throw new IllegalArgumentException("Mountain mask thresholds must satisfy 0 <= start < end <= 1");
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
