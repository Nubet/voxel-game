package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;

import com.norbertfila.generation.biome.BiomeProfile;
import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.generation.biome.PlantType;
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
    private final BiomeRegistry biomes;
    private final TerrainSource terrain;

    VegetationGenerator(long seed, Noise2D density, Noise2D forest) {
        this(seed, density, forest, BiomeRegistry.defaults());
    }

    VegetationGenerator(long seed, Noise2D density, Noise2D forest, BiomeRegistry biomes) {
        this(seed, density, forest, biomes, null);
    }

    VegetationGenerator(long seed, Noise2D density, Noise2D forest, BiomeRegistry biomes, TerrainSource terrain) {
        this.seed = seed;
        this.density = density;
        this.forest = forest;
        this.biomes = biomes;
        this.terrain = terrain;
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
                    int column = (int) (lz * Chunk.SIZE + lx);
                    BiomeProfile profile = biomes.profile(columns.biome(column));
                    if (!profile.vegetation().plants().contains(PlantType.FLOWER)) continue;
                    place((int) lx, (int) lz, oy, blocks, columns, flower);
                }
            }
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int column = z * Chunk.SIZE + x;
                long y = (long) columns.surfaceHeight(column) + 1 - oy;
                if (y < 0
                        || y >= Chunk.SIZE
                        || !columns.surfaceSupported(column)
                        || columns.surfaceHeight(column) < columns.waterLevel(column)) continue;
                if (blocks[((int) y * Chunk.SIZE + z) * Chunk.SIZE + x] != BlockId.AIR) continue;
                BiomeProfile profile = biomes.profile(columns.biome(column));
                boolean ordinary = profile.vegetation().plants().contains(PlantType.SHORT_GRASS)
                        || profile.vegetation().plants().contains(PlantType.FERN);
                PlantType special = specialPlant(
                        profile,
                        hash(seed ^ FLOWER_SALT, (long) ox + x, (long) oz + z),
                        columns.surfaceMaterial(column));
                if (special != null) {
                    boolean nearWater = special == PlantType.REED && nearWater(ox + x, oz + z, columns, column);
                    byte plant = block(special);
                    if (PlantRules.supports(plant, columns.surfaceMaterial(column), nearWater))
                        place(x, z, oy, blocks, columns, plant, nearWater);
                    continue;
                }
                if (!ordinary || !PlantRules.supports(columns.surfaceMaterial(column))) continue;
                double t =
                        Math.clamp((sample(density, ((long) ox + x) * .012, ((long) oz + z) * .012) + .4) / .9, 0, 1);
                if (unit(hash(seed ^ GRASS_SALT, (long) ox + x, (long) oz + z))
                        >= .3 * profile.vegetation().grassDensity() * t * t * (3 - 2 * t)) continue;
                double woodland = sample(forest, ((long) ox + x) * .003, ((long) oz + z) * .003);
                byte plant = woodland > .1
                                && profile.vegetation().plants().contains(PlantType.FERN)
                                && unit(hash(seed ^ FLOWER_SALT, (long) ox + x, (long) oz + z))
                                        < profile.vegetation().fernFraction()
                        ? BlockId.FERN
                        : BlockId.SHORT_GRASS;
                if (plant == BlockId.SHORT_GRASS
                        && !profile.vegetation().plants().contains(PlantType.SHORT_GRASS)) plant = BlockId.FERN;
                place(x, z, oy, blocks, columns, plant);
            }
    }

    private static void place(int x, int z, int oy, byte[] blocks, TerrainColumn columns, byte plant) {
        place(x, z, oy, blocks, columns, plant, false);
    }

    private static void place(
            int x, int z, int oy, byte[] blocks, TerrainColumn columns, byte plant, boolean nearWater) {
        int column = z * Chunk.SIZE + x;
        long y = (long) columns.surfaceHeight(column) + 1 - oy;
        if (y < 0
                || y >= Chunk.SIZE
                || !suitable(columns, column)
                || !PlantRules.supports(plant, columns.surfaceMaterial(column), nearWater)) return;
        int index = ((int) y * Chunk.SIZE + z) * Chunk.SIZE + x;
        if (blocks[index] == BlockId.AIR) blocks[index] = plant;
    }

    private static boolean suitable(TerrainColumn columns, int column) {
        return columns.surfaceSupported(column) && columns.surfaceHeight(column) >= columns.waterLevel(column);
    }

    private static PlantType specialPlant(BiomeProfile profile, long value, byte ground) {
        var plants = profile.vegetation().plants();
        double roll = unit(value);
        for (PlantType type : PlantType.values()) {
            double weight = switch (type) {
                case CACTUS -> .002;
                case DEAD_BUSH -> .01;
                case MUSHROOM -> .06;
                case REED -> .08;
                case BERRY_BUSH -> .005;
                case ALPINE_GRASS -> .12;
                default -> 0;
            };
            if (!plants.contains(type) && !(type == PlantType.REED && ground == BlockId.SAND)) continue;
            if (type == PlantType.MUSHROOM) weight *= profile.vegetation().specialDensity();
            if (roll < weight) return type;
            roll -= weight;
        }
        return null;
    }

    private static byte block(PlantType plant) {
        return switch (plant) {
            case CACTUS -> BlockId.CACTUS;
            case REED -> BlockId.REED;
            case ALPINE_GRASS -> BlockId.ALPINE_GRASS;
            case DEAD_BUSH -> BlockId.DEAD_BUSH;
            case MUSHROOM -> BlockId.MUSHROOM;
            case BERRY_BUSH -> BlockId.BERRY_BUSH;
            default -> throw new IllegalArgumentException("Not a special plant: " + plant);
        };
    }

    private boolean nearWater(int x, int z, TerrainColumn column, int index) {
        if (terrain == null) return Math.abs((long) column.surfaceHeight(index) - column.waterLevel(index)) <= 1;
        int y = column.surfaceHeight(index);
        for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            long wx = (long) x + d[0], wz = (long) z + d[1];
            if (wx < Integer.MIN_VALUE || wx > Integer.MAX_VALUE || wz < Integer.MIN_VALUE || wz > Integer.MAX_VALUE)
                continue;
            var neighbor = terrain.sample((int) wx, (int) wz);
            if (neighbor.materialAt(y) == BlockId.WATER) return true;
        }
        return false;
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
