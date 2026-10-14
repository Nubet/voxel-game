package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.PlantRules;

final class VegetationGenerator {
    private static final int PATCH_CELL_SIZE = 16;
    private static final int PATCH_RADIUS = 4;
    private static final int PATCH_ATTEMPTS = 16;
    private static final long FLOWER_SALT = 0x632BE59BD9B4E019L;
    private static final long GRASS_SALT = 0xDB4F0B9175AE2165L;
    private static final byte[] FLOWERS = {BlockId.DANDELION_YELLOW, BlockId.DANDELION_WHITE, BlockId.POPPY};
    private final long seed;
    private final Noise2D density;
    private final Noise2D forest;

    VegetationGenerator(long seed, Noise2D density, Noise2D forest) {
        this.seed = seed;
        this.density = density;
        this.forest = forest;
    }

    void generate(int ox, int oy, int oz, byte[] blocks, TerrainColumn columns) {
        long firstX = Math.floorDiv((long) ox - PATCH_RADIUS, PATCH_CELL_SIZE);
        long lastX = Math.floorDiv((long) ox + Chunk.SIZE - 1 + PATCH_RADIUS, PATCH_CELL_SIZE);
        long firstZ = Math.floorDiv((long) oz - PATCH_RADIUS, PATCH_CELL_SIZE);
        long lastZ = Math.floorDiv((long) oz + Chunk.SIZE - 1 + PATCH_RADIUS, PATCH_CELL_SIZE);
        for (long cz = firstZ; cz <= lastZ; cz++)
            for (long cx = firstX; cx <= lastX; cx++) {
                long patch = hash(seed ^ FLOWER_SALT, cx, cz);
                if (unit(patch) >= .3) continue;
                long centerX = cx * PATCH_CELL_SIZE + bounded(patch, 1, PATCH_CELL_SIZE);
                long centerZ = cz * PATCH_CELL_SIZE + bounded(patch, 2, PATCH_CELL_SIZE);
                byte flower = FLOWERS[bounded(patch, 3, FLOWERS.length)];
                for (int attempt = 0; attempt < PATCH_ATTEMPTS; attempt++) {
                    long x = centerX + bounded(patch, 4 + attempt * 2, 2 * PATCH_RADIUS + 1) - PATCH_RADIUS;
                    long z = centerZ + bounded(patch, 5 + attempt * 2, 2 * PATCH_RADIUS + 1) - PATCH_RADIUS;
                    long lx = x - ox, lz = z - oz;
                    if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) continue;
                    place((int) lx, (int) lz, oy, blocks, columns, flower);
                }
            }
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int column = z * Chunk.SIZE + x;
                long y = (long) columns.surfaceHeight(column) + 1 - oy;
                if (y < 0 || y >= Chunk.SIZE || !suitable(columns, column)) continue;
                if (blocks[((int) y * Chunk.SIZE + z) * Chunk.SIZE + x] != BlockId.AIR) continue;
                double t =
                        Math.clamp((sample(density, ((long) ox + x) * .012, ((long) oz + z) * .012) + .4) / .9, 0, 1);
                if (unit(hash(seed ^ GRASS_SALT, (long) ox + x, (long) oz + z)) >= .3 * t * t * (3 - 2 * t)) continue;
                double woodland = sample(forest, ((long) ox + x) * .003, ((long) oz + z) * .003);
                byte plant = woodland > .1 && unit(hash(seed ^ FLOWER_SALT, (long) ox + x, (long) oz + z)) < .2
                        ? BlockId.FERN
                        : BlockId.SHORT_GRASS;
                place(x, z, oy, blocks, columns, plant);
            }
    }

    private static void place(int x, int z, int oy, byte[] blocks, TerrainColumn columns, byte plant) {
        int column = z * Chunk.SIZE + x;
        long y = (long) columns.surfaceHeight(column) + 1 - oy;
        if (y < 0 || y >= Chunk.SIZE || !suitable(columns, column)) return;
        int index = ((int) y * Chunk.SIZE + z) * Chunk.SIZE + x;
        if (blocks[index] == BlockId.AIR) blocks[index] = plant;
    }

    private static boolean suitable(TerrainColumn columns, int column) {
        return PlantRules.supports(columns.surfaceMaterial(column))
                && columns.surfaceHeight(column) >= columns.waterLevel();
    }

    private static int bounded(long seed, int index, int bound) {
        return (int) Long.remainderUnsigned(hash(seed, index, 0), bound);
    }

    private static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double sample(Noise2D noise, double x, double z) {
        double value = noise.sample(x, z);
        if (!Double.isFinite(value) || value < -1 || value > 1)
            throw new IllegalStateException("Vegetation noise must be finite and within [-1, 1]");
        return value;
    }
}
