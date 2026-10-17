package com.norbertfila.generation.biome;

import com.norbertfila.world.BlockId;
import java.util.Arrays;
import java.util.Random;

public final class BadlandsSurface {
    private static final int BAND_COUNT = 192;
    private final byte[] bands = new byte[BAND_COUNT];

    public BadlandsSurface(long seed) {
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

    public byte bandAt(int worldY) {
        return bands[Math.floorMod(worldY, bands.length)];
    }
}
