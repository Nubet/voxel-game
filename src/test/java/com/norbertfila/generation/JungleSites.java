package com.norbertfila.generation;

import com.norbertfila.generation.biome.BiomeType;

final class JungleSites {
    record Site(int x, int z) {}

    static Site find(TerrainGenerator source, boolean mahogany) {
        var vegetation = new JungleVegetation(source.seed());
        int tropical = 0;
        String last = "";
        float warmHumidity = -1;
        for (int z = -8192; z <= 8192; z += 128)
            for (int x = -8192; x <= 8192; x += 128) {
                double variant = vegetation.mahogany(x, z);
                var ground = source.sample(x, z);
                if (ground.climate().temperature() > .2f)
                    warmHumidity = Math.max(warmHumidity, ground.climate().humidity());
                if (ground.biome() == BiomeType.JUNGLE) {
                    tropical++;
                    last = x + "," + z + " y=" + ground.surfaceHeight();
                }
                if (mahogany ? variant < .8 : variant > .15) continue;
                if (ground.biome() != BiomeType.JUNGLE || ground.surfaceHeight() < 32 || ground.surfaceHeight() > 70)
                    continue;
                boolean interior = true;
                for (int dz : new int[] {-32, 0, 32})
                    for (int dx : new int[] {-32, 0, 32})
                        if (source.sample(x + dx, z + dz).biome() != BiomeType.JUNGLE) interior = false;
                if (interior && !source.canopies(x - 32, z - 32, 64).isEmpty()) return new Site(x, z);
            }
        throw new AssertionError("Missing Jungle scene for seed=" + source.seed() + " mahogany=" + mahogany
                + " tropical=" + tropical + " last=" + last + " warmHumidity=" + warmHumidity);
    }
}
