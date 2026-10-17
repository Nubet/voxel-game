package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.world.*;
import java.util.List;
import java.util.function.BiPredicate;

final class JungleVegetation {
    private final Noise2D understory;
    private final Noise2D soil;
    private final Noise2D mahogany;
    private final long seed;

    JungleVegetation(long seed) {
        this.seed = seed;
        understory = new PerlinNoise(seed ^ 0x517CC1B727220A95L);
        soil = new PerlinNoise(seed ^ 0x6C8E9CF570932BD5L);
        mahogany = new PerlinNoise(seed ^ 0x082EFA98EC4E6C89L);
    }

    double mahogany(int x, int z) {
        return com.norbertfila.generation.biome.JungleRules.smooth(mahogany.sample(x / 210d, z / 210d), -.04, .16);
    }

    double understory(int x, int z) {
        return understory.sample(x / 45d, z / 45d);
    }

    byte surface(int x, int z, TerrainSummary ground, List<TreeGenerator.Tree> trees) {
        if (ground.biome() != BiomeType.JUNGLE || ground.surfaceMaterial() != BlockId.GRASS)
            return ground.surfaceMaterial();
        double patch = soil.sample(x / 22d, z / 22d);
        if (ground.surfaceHeight() <= (long) ground.waterLevel() + 5 && patch < -.18) return BlockId.MUD;
        boolean shaded = TreeGenerator.canopyHeight(trees, x, z) != Long.MIN_VALUE;
        return shaded && patch > .20 - .05 * mahogany(x, z) ? BlockId.PODZOL : BlockId.GRASS;
    }

    void generate(
            int ox, int oy, int oz, byte[] blocks, GroundSampler terrain, BiPredicate<Integer, Integer> supported) {
        for (long cz = Math.floorDiv((long) oz - 3, 6); cz <= Math.floorDiv((long) oz + 18, 6); cz++)
            for (long cx = Math.floorDiv((long) ox - 3, 6); cx <= Math.floorDiv((long) ox + 18, 6); cx++) {
                long wx = cx * 6 + 2 + Math.floorMod(hash(seed, cx, cz), 2);
                long wz = cz * 6 + 2 + Math.floorMod(hash(seed ^ 31, cx, cz), 2);
                if (wx < Integer.MIN_VALUE + 3
                        || wx > Integer.MAX_VALUE - 3
                        || wz < Integer.MIN_VALUE + 3
                        || wz > Integer.MAX_VALUE - 3) continue;
                int x = (int) wx, z = (int) wz;
                if (understory(x, z) < .035 + .07 * mahogany(x, z) || Math.floorMod(hash(seed ^ 71, cx, cz), 4) == 0)
                    continue;
                var ground = terrain.sample(x, z);
                int height = ground.surfaceHeight();
                if (ground.biome() != BiomeType.JUNGLE
                        || !PlantRules.supports(ground.surfaceMaterial())
                        || height <= ground.waterLevel()
                        || !supported.test(x, z)) continue;
                if (Math.abs((long) terrain.surfaceHeight(x + 2, z) - height) > 1
                        || Math.abs((long) terrain.surfaceHeight(x - 2, z) - height) > 1
                        || Math.abs((long) terrain.surfaceHeight(x, z + 2) - height) > 1
                        || Math.abs((long) terrain.surfaceHeight(x, z - 2) - height) > 1) continue;
                place(x, height + 1, z, BlockId.JUNGLE_LOG, ox, oy, oz, blocks);
                var crown = new TreePlan.Crown(x, height + 2, z, 2, 1, hash(seed, cx, cz));
                for (int dy = 1; dy <= 3; dy++)
                    for (int dz = -2; dz <= 2; dz++)
                        for (int dx = -2; dx <= 2; dx++)
                            if (crown.contains(x + dx, height + dy, z + dz))
                                place(x + dx, height + dy, z + dz, BlockId.LEAVES, ox, oy, oz, blocks);
            }
    }

    static void place(int x, int y, int z, byte block, int ox, int oy, int oz, byte[] blocks) {
        long lx = (long) x - ox, ly = (long) y - oy, lz = (long) z - oz;
        if (lx < 0 || lx >= 16 || ly < 0 || ly >= 16 || lz < 0 || lz >= 16 || !WorldBounds.containsY(y)) return;
        int index = ((int) ly * 16 + (int) lz) * 16 + (int) lx;
        if (blocks[index] == BlockId.AIR) blocks[index] = block;
    }
}
