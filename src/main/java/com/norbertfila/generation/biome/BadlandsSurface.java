package com.norbertfila.generation.biome;

import com.norbertfila.generation.Noise2D;
import com.norbertfila.generation.PerlinNoise;
import com.norbertfila.world.BlockId;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public final class BadlandsSurface {
    public static final int TERRACOTTA_DEPTH = 96;
    private static final int BAND_COUNT = 192;
    private final byte[] bands = new byte[BAND_COUNT];
    private final Noise2D offsetNoise;

    public BadlandsSurface(long seed) {
        offsetNoise = new PerlinNoise(seed ^ 0xBB67AE8584CAA73BL);
        var random = new Random(seed ^ 0x6A09E667F3BCC909L);
        Arrays.fill(bands, BlockId.TERRACOTTA);
        for (int y = random.nextInt(5); y < bands.length; y += random.nextInt(2, 6))
            bands[y] = BlockId.TERRACOTTA_ORANGE;
        addBands(random, BlockId.TERRACOTTA_YELLOW);
        addBands(random, BlockId.TERRACOTTA_BROWN);
        addBands(random, BlockId.TERRACOTTA_RED);
        for (int y = random.nextInt(12); y < bands.length; y += random.nextInt(20, 36)) {
            bands[y] = BlockId.TERRACOTTA_WHITE;
            bands[Math.floorMod(y + (random.nextBoolean() ? -1 : 1), bands.length)] = BlockId.TERRACOTTA_LIGHT_GRAY;
        }
    }

    private void addBands(Random random, byte material) {
        int count = random.nextInt(4, 8);
        for (int band = 0; band < count; band++) {
            int start = random.nextInt(bands.length);
            int width = random.nextInt(1, 4);
            for (int y = start; y < start + width; y++) bands[y % bands.length] = material;
        }
    }

    public byte bandAt(long worldY) {
        return bands[Math.floorMod(worldY, bands.length)];
    }

    public Column column(int worldX, int worldZ) {
        int offset = (int) Math.round(Math.clamp(offsetNoise.sample(worldX / 256d, worldZ / 256d), -1, 1) * 7);
        return new Column(this, offset);
    }

    public record Column(BadlandsSurface surface, int offset) {
        public Column {
            Objects.requireNonNull(surface);
            if (offset < -7 || offset > 7) throw new IllegalArgumentException("Badlands band offset outside [-7, 7]");
        }

        public byte bandAt(int worldY) {
            return surface.bandAt((long) worldY + offset);
        }

        public byte surfaceAt(int height, int seaLevel) {
            return height >= seaLevel && (long) height <= (long) seaLevel + 32 ? BlockId.RED_SAND : bandAt(height);
        }

        public byte materialAt(int worldY, int surfaceHeight, byte topMaterial) {
            long depth = (long) surfaceHeight - worldY;
            if (depth < 0) throw new IllegalArgumentException("Badlands material requested above surface");
            if (depth == 0) return topMaterial;
            if (depth > TERRACOTTA_DEPTH) return BlockId.STONE;
            if (topMaterial == BlockId.RED_SAND && depth <= 3) return BlockId.TERRACOTTA_ORANGE;
            return bandAt(worldY);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Column column
                    && offset == column.offset
                    && Arrays.equals(surface.bands, column.surface.bands);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(surface.bands) + offset;
        }
    }
}
