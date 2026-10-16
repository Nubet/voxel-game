package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;

import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import java.util.ArrayList;
import java.util.List;

final class TreeGenerator {
    private static final int CELL_SIZE = 6;
    private static final int MIN_SPACING = 4;
    private static final int MAX_CROWN_RADIUS = 3;
    private static final int SLOPE_SAMPLE_DISTANCE = 2;
    private static final int MAX_SLOPE = 2;
    private static final long PRIORITY_SALT = 0xD1B54A32D192ED03L;
    private static final long POSITION_SALT = 0x9E3779B97F4A7C15L;
    private static final long SHAPE_SALT = 0x94D049BB133111EBL;

    private final long seed;
    private final Noise2D forestDensity;
    private final TerrainSource terrain;
    private final BiomeRegistry biomes;

    TreeGenerator(long seed, Noise2D forestDensity, TerrainSource terrain) {
        this(seed, forestDensity, terrain, BiomeRegistry.defaults());
    }

    TreeGenerator(long seed, Noise2D forestDensity, TerrainSource terrain, BiomeRegistry biomes) {
        this.seed = seed;
        this.forestDensity = forestDensity;
        this.terrain = terrain;
        this.biomes = biomes;
    }

    void generate(int originX, int originY, int originZ, byte[] blocks) {
        place(trees(originX, originZ), originX, originY, originZ, blocks);
    }

    /** Trees whose trunk or crown can reach the column at the given origin, in the order they must be placed. */
    List<Tree> trees(int originX, int originZ) {
        long firstCellX = Math.floorDiv((long) originX - MAX_CROWN_RADIUS - CELL_SIZE + 1, CELL_SIZE);
        long lastCellX = Math.floorDiv((long) originX + Chunk.SIZE - 1 + MAX_CROWN_RADIUS, CELL_SIZE);
        long firstCellZ = Math.floorDiv((long) originZ - MAX_CROWN_RADIUS - CELL_SIZE + 1, CELL_SIZE);
        long lastCellZ = Math.floorDiv((long) originZ + Chunk.SIZE - 1 + MAX_CROWN_RADIUS, CELL_SIZE);
        List<Tree> trees = new ArrayList<>();
        for (long cellZ = firstCellZ; cellZ <= lastCellZ; cellZ++)
            for (long cellX = firstCellX; cellX <= lastCellX; cellX++) {
                Candidate candidate = acceptedTree(cellX, cellZ);
                if (candidate != null && suitable(candidate.tree.x, candidate.tree.z, candidate.terrain))
                    trees.add(candidate.tree);
            }
        return List.copyOf(trees);
    }

    // Order matters because logs overwrite leaves of trees placed earlier, while leaves only fill air.
    static void place(List<Tree> trees, int originX, int originY, int originZ, byte[] blocks) {
        for (Tree tree : trees) {
            placeTrunk(tree, originX, originY, originZ, blocks);
            placeCrown(tree, originX, originY, originZ, blocks);
        }
    }

    private Candidate acceptedTree(long cellX, long cellZ) {
        long candidateX = treeX(cellX, cellZ), candidateZ = treeZ(cellX, cellZ);
        if (!selected(cellX, cellZ, candidateX, candidateZ)) return null;
        int x = (int) candidateX, z = (int) candidateZ;
        long priority = hash(seed ^ PRIORITY_SALT, cellX, cellZ);
        for (long dz = -1; dz <= 1; dz++)
            for (long dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                long otherCellX = cellX + dx, otherCellZ = cellZ + dz;
                long otherX = treeX(otherCellX, otherCellZ), otherZ = treeZ(otherCellX, otherCellZ);
                long offsetX = candidateX - otherX;
                long offsetZ = candidateZ - otherZ;
                if (offsetX * offsetX + offsetZ * offsetZ >= (long) MIN_SPACING * MIN_SPACING) continue;
                long otherPriority = hash(seed ^ PRIORITY_SALT, otherCellX, otherCellZ);
                int priorityOrder = Long.compareUnsigned(priority, otherPriority);
                boolean otherWins = priorityOrder < 0
                        || priorityOrder == 0 && (otherCellZ < cellZ || otherCellZ == cellZ && otherCellX < cellX);
                if (otherWins && selected(otherCellX, otherCellZ, otherX, otherZ) && suitableCandidate(otherX, otherZ))
                    return null;
            }
        long shapeSeed = hash(seed ^ SHAPE_SALT, cellX, cellZ);
        TerrainSummary sample = terrain.sample(x, z);
        return new Candidate(
                new Tree(
                        x,
                        z,
                        sample.surfaceHeight() + 1,
                        4 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 1), 4),
                        2 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 2), 2),
                        shapeSeed),
                sample);
    }

    private boolean selected(long cellX, long cellZ, long x, long z) {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
            return false;
        double densityNoise = forestDensity.sample(x * .003, z * .003);
        if (!Double.isFinite(densityNoise) || densityNoise < -1 || densityNoise > 1)
            throw new IllegalStateException("Forest noise must be finite and within [-1, 1]");
        double t = Math.clamp((densityNoise + .1) / .5, 0, 1);
        double density =
                biomes.profile(terrain.sample((int) x, (int) z).biome()).trees().density() * t * t * (3 - 2 * t);
        return (hash(seed, cellX, cellZ) >>> 11) * 0x1.0p-53 < density;
    }

    private long treeX(long cellX, long cellZ) {
        return cellX * CELL_SIZE + Long.remainderUnsigned(hash(seed ^ POSITION_SALT, cellX, cellZ), CELL_SIZE);
    }

    private long treeZ(long cellX, long cellZ) {
        long positionSeed = hash(seed ^ POSITION_SALT, cellX, cellZ);
        return cellZ * CELL_SIZE + Long.remainderUnsigned(hash(positionSeed, cellZ, cellX), CELL_SIZE);
    }

    private boolean suitableCandidate(long x, long z) {
        TerrainSummary sample = terrain.sample((int) x, (int) z);
        return suitable((int) x, (int) z, sample);
    }

    private boolean suitable(int x, int z, TerrainSummary terrainSample) {
        if (terrainSample.surfaceMaterial() != BlockId.GRASS
                || terrainSample.surfaceHeight() <= terrainSample.waterLevel()
                || x < Integer.MIN_VALUE + SLOPE_SAMPLE_DISTANCE
                || x > Integer.MAX_VALUE - SLOPE_SAMPLE_DISTANCE
                || z < Integer.MIN_VALUE + SLOPE_SAMPLE_DISTANCE
                || z > Integer.MAX_VALUE - SLOPE_SAMPLE_DISTANCE) return false;
        int height = terrainSample.surfaceHeight();
        return Math.abs((long) height - terrain.surfaceHeight(x + SLOPE_SAMPLE_DISTANCE, z)) <= MAX_SLOPE
                && Math.abs((long) height - terrain.surfaceHeight(x - SLOPE_SAMPLE_DISTANCE, z)) <= MAX_SLOPE
                && Math.abs((long) height - terrain.surfaceHeight(x, z + SLOPE_SAMPLE_DISTANCE)) <= MAX_SLOPE
                && Math.abs((long) height - terrain.surfaceHeight(x, z - SLOPE_SAMPLE_DISTANCE)) <= MAX_SLOPE;
    }

    private static void placeTrunk(Tree tree, int originX, int originY, int originZ, byte[] blocks) {
        for (int dy = 0; dy < tree.trunkHeight; dy++)
            place(tree.x, (long) tree.baseY + dy, tree.z, originX, originY, originZ, blocks, BlockId.LOG);
    }

    private static void placeCrown(Tree tree, int originX, int originY, int originZ, byte[] blocks) {
        for (int layer = 0; layer < 5; layer++) {
            int radius = layer == 0 || layer == 4 ? 1 : tree.crownRadius;
            long y = (long) tree.baseY + tree.trunkHeight - 2 + layer;
            for (int dz = -radius; dz <= radius; dz++)
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1 || dx == 0 && dz == 0) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius
                            || Math.floorMod(hash(tree.shapeSeed, layer, dx * 31L + dz), 11) == 0) continue;
                    place((long) tree.x + dx, y, (long) tree.z + dz, originX, originY, originZ, blocks, BlockId.LEAVES);
                }
        }
    }

    private static void place(
            long worldX, long worldY, long worldZ, int originX, int originY, int originZ, byte[] blocks, byte block) {
        long x = worldX - originX, y = worldY - originY, z = worldZ - originZ;
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) return;
        int index = ((int) y * Chunk.SIZE + (int) z) * Chunk.SIZE + (int) x;
        if (blocks[index] == BlockId.AIR || block == BlockId.LOG && blocks[index] == BlockId.LEAVES)
            blocks[index] = block;
    }

    record Tree(int x, int z, int baseY, int trunkHeight, int crownRadius, long shapeSeed) {}

    private record Candidate(Tree tree, TerrainSummary terrain) {}
}
