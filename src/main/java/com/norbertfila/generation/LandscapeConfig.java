package com.norbertfila.generation;

import java.util.Objects;

public record LandscapeConfig(
        Field regions, Field woodland, Field vegetation, Field flowers, Field relief, Style classic, Style modern) {
    public LandscapeConfig {
        Objects.requireNonNull(regions);
        Objects.requireNonNull(woodland);
        Objects.requireNonNull(vegetation);
        Objects.requireNonNull(flowers);
        Objects.requireNonNull(relief);
        Objects.requireNonNull(classic);
        Objects.requireNonNull(modern);
    }

    public static LandscapeConfig defaults() {
        return new LandscapeConfig(
                new Field(1536, 2.5, .5),
                new Field(192, 2, .5),
                new Field(96, 2, .5),
                new Field(320, 2.5, .5),
                new Field(384, 2, .5),
                new Style(new Range(0, 1.6), new Range(.15, 1.2), new Range(0, 1.6), new Range(.25, 1), .4, .3, .45),
                new Style(new Range(.35, 1.35), new Range(.5, 1.5), new Range(0, 2), new Range(.4, 1), 1, 1, 1));
    }

    public record Field(double wavelength, double contrast, double bias) {
        public Field {
            if (!Double.isFinite(wavelength)
                    || wavelength < 32
                    || !Double.isFinite(contrast)
                    || contrast < 0
                    || contrast > 8
                    || !Double.isFinite(bias)
                    || bias < 0
                    || bias > 1) throw new IllegalArgumentException("Invalid landscape field");
        }
    }

    public record Range(double min, double max) {
        public Range {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0 || min > max || max > 2)
                throw new IllegalArgumentException("Landscape range must be ordered and within [0, 2]");
        }

        double at(double value) {
            return min + (max - min) * value;
        }
    }

    public record Style(
            Range trees,
            Range vegetation,
            Range flowers,
            Range hills,
            double mountains,
            double warp,
            double elevation) {
        public Style {
            Objects.requireNonNull(trees);
            Objects.requireNonNull(vegetation);
            Objects.requireNonNull(flowers);
            Objects.requireNonNull(hills);
            if (hills.max() > 1 || !unit(mountains) || !unit(warp) || !unit(elevation))
                throw new IllegalArgumentException("Terrain scales must be within [0, 1]");
        }

        private static boolean unit(double value) {
            return Double.isFinite(value) && value >= 0 && value <= 1;
        }
    }
}
