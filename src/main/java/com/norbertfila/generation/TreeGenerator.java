package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;

import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.generation.biome.TreeType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.PlantRules;
import java.util.ArrayList;
import java.util.List;

final class TreeGenerator {
    private static final int CELL_SIZE = 6;
    private static final int MIN_SPACING = 4;
    private static final int MAX_CROWN_RADIUS = 9;
    private static final int SLOPE_SAMPLE_DISTANCE = 2;
    private static final int MAX_SLOPE = 2;
    private static final long PRIORITY_SALT = 0xD1B54A32D192ED03L;
    private static final long POSITION_SALT = 0x9E3779B97F4A7C15L;
    private static final long SHAPE_SALT = 0x94D049BB133111EBL;

    private final long seed;
    private final Noise2D forestDensity;
    private final GroundSampler terrain;
    private final BiomeRegistry biomes;

    TreeGenerator(long seed, Noise2D forestDensity, TerrainSource terrain) {
        this(seed, forestDensity, terrain, BiomeRegistry.defaults());
    }

    TreeGenerator(long seed, Noise2D forestDensity, TerrainSource terrain, BiomeRegistry biomes) {
        this(seed, forestDensity, terrain::sample, biomes);
    }

    TreeGenerator(long seed, Noise2D forestDensity, GroundSampler terrain, BiomeRegistry biomes) {
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

    // Place all wood first so overlapping crowns cannot replace another tree's branches.
    static void place(List<Tree> trees, int originX, int originY, int originZ, byte[] blocks) {
        for (Tree tree : trees) {
            if (tree.plan != null)
                tree.plan.wood((x, y, z, block) -> place(x, y, z, originX, originY, originZ, blocks, block));
            else placeTrunk(tree, originX, originY, originZ, blocks);
        }
        for (Tree tree : trees) {
            if (tree.plan != null)
                tree.plan.foliage((x, y, z, block) -> place(x, y, z, originX, originY, originZ, blocks, block));
            else placeCrown(tree, originX, originY, originZ, blocks);
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
        TreeType type = biomes.profile(sample.biome()).trees().type();
        if (type == TreeType.JUNGLE) {
            int roll = JungleTrees.bounded(shapeSeed, 17, 100);
            type = roll < 8 ? TreeType.GIANT_JUNGLE : TreeType.JUNGLE;
            if (x < Integer.MIN_VALUE + 10
                    || x > Integer.MAX_VALUE - 10
                    || z < Integer.MIN_VALUE + 10
                    || z > Integer.MAX_VALUE - 10) return null;
            if (type == TreeType.GIANT_JUNGLE) {
                for (int dz = 0; dz < 2; dz++)
                    for (int dx = 0; dx < 2; dx++) {
                        var support = terrain.sample(x + dx, z + dz);
                        if (!suitable(x + dx, z + dz, support) || support.surfaceHeight() != sample.surfaceHeight())
                            return null;
                    }
            }
            var plan = JungleTrees.plan(x, sample.surfaceHeight() + 1, z, shapeSeed, type);
            if (plan.maxY() >= com.norbertfila.world.WorldBounds.MAX_Y_EXCLUSIVE) return null;
            return new Candidate(
                    new Tree(
                            x,
                            z,
                            sample.surfaceHeight() + 1,
                            plan.maxY() - sample.surfaceHeight() - 2,
                            9,
                            shapeSeed,
                            type,
                            plan),
                    sample);
        }
        int trunkHeight = type == TreeType.CONIFER
                ? 5 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 1), 4)
                : type == TreeType.SWAMP
                        ? 3 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 1), 3)
                        : 4 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 1), 4);
        int crownRadius = type == TreeType.CONIFER
                ? 2
                : type == TreeType.SWAMP ? 2 : 2 + (int) Long.remainderUnsigned(hash(shapeSeed, 0, 2), 2);
        return new Candidate(
                new Tree(x, z, sample.surfaceHeight() + 1, trunkHeight, crownRadius, shapeSeed, type), sample);
    }

    private boolean selected(long cellX, long cellZ, long x, long z) {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
            return false;
        double densityNoise = forestDensity.sample(x * .003, z * .003);
        if (!Double.isFinite(densityNoise) || densityNoise < -1 || densityNoise > 1)
            throw new IllegalStateException("Forest noise must be finite and within [-1, 1]");
        double t = Math.clamp((densityNoise + .1) / .5, 0, 1);
        var profile = biomes.profile(terrain.sample((int) x, (int) z).biome()).trees();
        double density = profile.density() * (profile.type() == TreeType.JUNGLE ? .85 + .15 * t : t * t * (3 - 2 * t));
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
        if (!PlantRules.supports(terrainSample.surfaceMaterial())
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
            place(tree.x, (long) tree.baseY + dy, tree.z, originX, originY, originZ, blocks, log(tree));
    }

    private static void placeCrown(Tree tree, int originX, int originY, int originZ, byte[] blocks) {
        int layers = crownLayers(tree);
        for (int layer = 0; layer < layers; layer++) {
            int radius = crownRadius(tree, layer);
            long y = (long) tree.baseY + tree.trunkHeight - 2 + layer;
            for (int dz = -radius; dz <= radius; dz++)
                for (int dx = -radius; dx <= radius; dx++) {
                    if (!hasLeaf(tree, layer, dx, dz)) continue;
                    place((long) tree.x + dx, y, (long) tree.z + dz, originX, originY, originZ, blocks, leaves(tree));
                    if (tree.type == TreeType.SWAMP && layer == 1 && (Math.abs(dx) == radius || Math.abs(dz) == radius))
                        place(
                                (long) tree.x + dx,
                                y - 1,
                                (long) tree.z + dz,
                                originX,
                                originY,
                                originZ,
                                blocks,
                                leaves(tree));
                }
        }
    }

    static long canopyHeight(List<Tree> trees, int x, int z) {
        long highest = Long.MIN_VALUE, highestLog = Long.MIN_VALUE;
        for (Tree tree : trees) {
            if (tree.plan != null) {
                highest = Math.max(highest, tree.plan.canopyHeight(x, z));
                continue;
            }
            long dx = (long) x - tree.x, dz = (long) z - tree.z;
            if (dx == 0 && dz == 0) highestLog = Math.max(highestLog, (long) tree.baseY + tree.trunkHeight - 1);
            if (Math.abs(dx) > tree.crownRadius || Math.abs(dz) > tree.crownRadius) continue;
            for (int layer = crownLayers(tree) - 1; layer >= 0; layer--) {
                if (!hasLeaf(tree, layer, (int) dx, (int) dz)) continue;
                highest = Math.max(highest, (long) tree.baseY + tree.trunkHeight - 2 + layer);
                break;
            }
        }
        return highest > highestLog ? highest : Long.MIN_VALUE;
    }

    private static int crownLayers(Tree tree) {
        return tree.type == TreeType.CONIFER ? 6 : tree.type == TreeType.SWAMP ? 4 : 5;
    }

    private static int crownRadius(Tree tree, int layer) {
        int layers = crownLayers(tree);
        return tree.type == TreeType.CONIFER
                ? Math.max(0, Math.min(tree.crownRadius, (layers - 1 - layer) / 2))
                : layer == 0 || layer == layers - 1 ? 1 : tree.crownRadius;
    }

    private static boolean hasLeaf(Tree tree, int layer, int dx, int dz) {
        int radius = crownRadius(tree, layer);
        return Math.abs(dx) <= radius
                && Math.abs(dz) <= radius
                && Math.abs(dx) + Math.abs(dz) <= radius + 1
                && !(dx == 0 && dz == 0 && layer < 2)
                && !(radius > 0 && Math.abs(dx) == radius && Math.abs(dz) == radius)
                && Math.floorMod(hash(tree.shapeSeed, layer, dx * 31L + dz), 11) != 0;
    }

    private static void place(
            long worldX, long worldY, long worldZ, int originX, int originY, int originZ, byte[] blocks, byte block) {
        long x = worldX - originX, y = worldY - originY, z = worldZ - originZ;
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) return;
        int index = ((int) y * Chunk.SIZE + (int) z) * Chunk.SIZE + (int) x;
        if (blocks[index] == BlockId.AIR || isLog(block) && isLeaves(blocks[index])) blocks[index] = block;
    }

    private static boolean isLog(byte block) {
        return block == BlockId.LOG
                || block == BlockId.PINE_LOG
                || block == BlockId.SWAMP_LOG
                || block == BlockId.JUNGLE_LOG;
    }

    private static boolean isLeaves(byte block) {
        return block == BlockId.LEAVES
                || block == BlockId.PINE_NEEDLES
                || block == BlockId.SWAMP_LEAVES
                || block == BlockId.JUNGLE_LEAVES;
    }

    private static byte log(Tree tree) {
        return tree.type == TreeType.CONIFER
                ? BlockId.PINE_LOG
                : tree.type == TreeType.SWAMP ? BlockId.SWAMP_LOG : BlockId.LOG;
    }

    private static byte leaves(Tree tree) {
        return tree.type == TreeType.CONIFER
                ? BlockId.PINE_NEEDLES
                : tree.type == TreeType.SWAMP ? BlockId.SWAMP_LEAVES : BlockId.LEAVES;
    }

    record Tree(
            int x, int z, int baseY, int trunkHeight, int crownRadius, long shapeSeed, TreeType type, TreePlan plan) {
        Tree(int x, int z, int baseY, int trunkHeight, int crownRadius, long shapeSeed, TreeType type) {
            this(x, z, baseY, trunkHeight, crownRadius, shapeSeed, type, null);
        }

        int maxY() {
            return plan == null ? baseY + trunkHeight + 4 : plan.maxY();
        }
    }

    private record Candidate(Tree tree, TerrainSummary terrain) {}
}
