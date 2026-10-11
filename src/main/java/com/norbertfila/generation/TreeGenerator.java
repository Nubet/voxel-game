package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;

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

    TreeGenerator(long seed, Noise2D forestDensity, TerrainSource terrain) {
        this.seed = seed;
        this.forestDensity = forestDensity;
        this.terrain = terrain;
    }

    void generate(int originX, int originY, int originZ, byte[] blocks) {
        long firstCellX = Math.floorDiv((long) originX - MAX_CROWN_RADIUS - CELL_SIZE + 1, CELL_SIZE);
        long lastCellX = Math.floorDiv((long) originX + Chunk.SIZE - 1 + MAX_CROWN_RADIUS, CELL_SIZE);
        long firstCellZ = Math.floorDiv((long) originZ - MAX_CROWN_RADIUS - CELL_SIZE + 1, CELL_SIZE);
        long lastCellZ = Math.floorDiv((long) originZ + Chunk.SIZE - 1 + MAX_CROWN_RADIUS, CELL_SIZE);

        for (long cellZ = firstCellZ; cellZ <= lastCellZ; cellZ++)
            for (long cellX = firstCellX; cellX <= lastCellX; cellX++) {
                Tree tree = acceptedTree(cellX, cellZ);
                if (tree != null && suitable(tree)) {
                    placeTrunk(tree, originX, originY, originZ, blocks);
                    placeCrown(tree, originX, originY, originZ, blocks);
                }
            }
    }

    private Tree acceptedTree(long cellX, long cellZ) {
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
        return new Tree(
                x,
                z,
                sample.surfaceHeight() + 1,
                4 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 1), 4),
                2 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 2), 2),
                shapeSeed,
                sample.surfaceMaterial());
    }

    private boolean selected(long cellX, long cellZ, long x, long z) {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
            return false;
        double densityNoise = forestDensity.sample(x * .003, z * .003);
        if (!Double.isFinite(densityNoise) || densityNoise < -1 || densityNoise > 1)
            throw new IllegalStateException("Forest noise must be finite and within [-1, 1]");
        double t = Math.clamp((densityNoise + .25) / .7, 0, 1);
        double density = .65 * t * t * (3 - 2 * t);
        return (hash(seed, cellX, cellZ) >>> 11) * 0x1.0p-53 < density;
    }

    private long treeX(long cellX, long cellZ) {
        return cellX * CELL_SIZE + Long.remainderUnsigned(hash(seed ^ POSITION_SALT, cellX, cellZ), CELL_SIZE);
    }

    private long treeZ(long cellX, long cellZ) {
        long positionSeed = hash(seed ^ POSITION_SALT, cellX, cellZ);
        return cellZ * CELL_SIZE + Long.remainderUnsigned(hash(positionSeed, cellZ, cellX), CELL_SIZE);
    }

    private boolean suitable(Tree tree) {
        return suitable(tree.x, tree.z, tree.surface);
    }

    private boolean suitableCandidate(long x, long z) {
        TerrainSummary sample = terrain.sample((int) x, (int) z);
        return suitable((int) x, (int) z, sample.surfaceMaterial());
    }

    private boolean suitable(int x, int z, byte surface) {
        if (surface != BlockId.GRASS
                || x < Integer.MIN_VALUE + SLOPE_SAMPLE_DISTANCE
                || x > Integer.MAX_VALUE - SLOPE_SAMPLE_DISTANCE
                || z < Integer.MIN_VALUE + SLOPE_SAMPLE_DISTANCE
                || z > Integer.MAX_VALUE - SLOPE_SAMPLE_DISTANCE) return false;
        int height = terrain.surfaceHeight(x, z);
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

    private static long hash(long salt, long x, long z) {
        long value = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record Tree(int x, int z, int baseY, int trunkHeight, int crownRadius, long shapeSeed, byte surface) {}
}
