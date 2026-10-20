package com.norbertfila.weather;

import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.biome.BiomeType;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class BiomeWeather {
    private static final int SPACING = 16;
    private static final int CACHE_LIMIT = 256;
    private final TerrainSource terrain;
    private final LinkedHashMap<Long, LocalWeather> samples = new LinkedHashMap<>(64, .75f, true);

    public BiomeWeather(TerrainSource terrain) {
        this.terrain = Objects.requireNonNull(terrain);
    }

    public LocalWeather sample(double x, double z) {
        if (!Double.isFinite(x)
                || !Double.isFinite(z)
                || x < Integer.MIN_VALUE
                || z < Integer.MIN_VALUE
                || x >= (long) Integer.MAX_VALUE - SPACING
                || z >= (long) Integer.MAX_VALUE - SPACING)
            throw new IllegalArgumentException("Invalid local weather position");
        int gx = Math.floorDiv((int) Math.floor(x), SPACING) * SPACING;
        int gz = Math.floorDiv((int) Math.floor(z), SPACING) * SPACING;
        float tx = smooth((float) ((x - gx) / SPACING));
        float tz = smooth((float) ((z - gz) / SPACING));
        return corner(gx, gz)
                .mix(corner(gx + SPACING, gz), tx)
                .mix(corner(gx, gz + SPACING).mix(corner(gx + SPACING, gz + SPACING), tx), tz);
    }

    private LocalWeather corner(int x, int z) {
        long key = ((long) x << 32) ^ Integer.toUnsignedLong(z);
        LocalWeather cached = samples.get(key);
        if (cached != null) return cached;
        var column = terrain.sample(x, z);
        var result = forBiome(column.biome(), terrain.surfaceTemperature(column));
        samples.put(key, result);
        if (samples.size() > CACHE_LIMIT)
            samples.remove(samples.keySet().iterator().next());
        return result;
    }

    public static LocalWeather forBiome(BiomeType biome, float temperature) {
        if (!Float.isFinite(temperature)) throw new IllegalArgumentException("Non-finite temperature");
        float amount = switch (biome) {
            case DESERT, CLASSIC_DESERT, BADLANDS -> 0;
            case SAVANNA, SHRUBLAND -> .4f;
            case FOREST, CLASSIC_FOREST, SEASONAL_FOREST -> 1.1f;
            case JUNGLE, RAINFOREST -> 1.35f;
            case SWAMP, SWAMPLAND -> 1.15f;
            case TAIGA, STONY_SLOPES -> .9f;
            case PLAINS,
                    CLASSIC_PLAINS,
                    SNOWY_TAIGA,
                    TUNDRA,
                    ALPINE,
                    OCEAN,
                    LAKE,
                    RIVER,
                    ARCHLANDS_CLASSIC,
                    FLOATING_ISLES_CLASSIC,
                    OVERHANG_HIGHLANDS_CLASSIC,
                    HIDDEN_VALLEYS_CLASSIC,
                    COASTAL_CLIFFS_CLASSIC -> 1;
        };
        float snow = switch (biome) {
            case SNOWY_TAIGA, TUNDRA, ALPINE -> 1;
            default -> smooth((-.4f - temperature) / .1f);
        };
        return new LocalWeather(amount * (1 - snow), amount * snow);
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
