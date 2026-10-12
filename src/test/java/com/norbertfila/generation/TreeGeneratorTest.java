package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeGeneratorTest {
    private static final TerrainSource FLAT_GRASS = terrain((x, z) -> 8);

    @Test
    void chunkClippingAndTreePlacementDoNotDependOnGenerationOrder() {
        TreeGenerator generator = new TreeGenerator(0, (x, z) -> 1, FLAT_GRASS);
        ChunkPos[] positions = {new ChunkPos(-1, 0, 0), new ChunkPos(0, 0, 0), new ChunkPos(1, 0, 0)};
        int[] expected = Arrays.stream(positions)
                .mapToInt(position -> Arrays.hashCode(generate(generator, position.x() * Chunk.SIZE, 0, 0)))
                .toArray();
        for (int i = positions.length - 1; i >= 0; i--)
            assertEquals(expected[i], Arrays.hashCode(generate(generator, positions[i].x() * Chunk.SIZE, 0, 0)));

        byte[] left = generate(generator, 0, 0, 0);
        byte[] right = generate(generator, Chunk.SIZE, 0, 0);
        assertTrue(has(left, BlockId.LOG));
        assertTrue(has(left, BlockId.LEAVES));
        assertTrue(has(right, BlockId.LOG));
        assertTrue(has(right, BlockId.LEAVES));
        assertTrue(hasLeavesNearEdge(left, Chunk.SIZE - 2, Chunk.SIZE - 1));
        assertTrue(hasLeavesNearEdge(right, 0, 1));
    }

    @Test
    void acceptedTrunksHaveTheMinimumSpacingAndVariedHeights() {
        TreeGenerator generator = new TreeGenerator(42, (x, z) -> 1, FLAT_GRASS);
        Set<Long> trunks = new HashSet<>();
        Set<Integer> heights = new HashSet<>();
        for (int chunkZ = -3; chunkZ <= 3; chunkZ++)
            for (int chunkX = -3; chunkX <= 3; chunkX++) {
                byte[] blocks = generate(generator, chunkX * Chunk.SIZE, 0, chunkZ * Chunk.SIZE);
                for (int z = 0; z < Chunk.SIZE; z++)
                    for (int x = 0; x < Chunk.SIZE; x++) {
                        int index = (9 * Chunk.SIZE + z) * Chunk.SIZE + x;
                        if (blocks[index] != BlockId.LOG) continue;
                        long worldX = (long) chunkX * Chunk.SIZE + x;
                        long worldZ = (long) chunkZ * Chunk.SIZE + z;
                        long key = (worldX << 32) ^ (worldZ & 0xffffffffL);
                        trunks.add(key);
                        int height = 0;
                        for (int y = 9; y < Chunk.SIZE; y++)
                            if (blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] == BlockId.LOG) height++;
                        heights.add(height);
                    }
            }
        assertTrue(trunks.size() > 10);
        assertTrue(heights.size() > 1);
        long[] positions = trunks.stream().mapToLong(Long::longValue).toArray();
        for (int i = 0; i < positions.length; i++)
            for (int j = i + 1; j < positions.length; j++) {
                long dx = (int) (positions[i] >> 32) - (int) (positions[j] >> 32);
                long dz = (int) positions[i] - (int) positions[j];
                assertTrue(dx * dx + dz * dz >= 16);
            }
    }

    @Test
    void lowFrequencyDensitySeparatesOpenAndWoodedRegions() {
        TreeGenerator generator = new TreeGenerator(9, (x, z) -> Math.clamp(x, -1, 1), FLAT_GRASS);
        int openTrees = countTrees(generator, -64, -33);
        int forestTrees = countTrees(generator, 33, 64);
        assertEquals(0, openTrees);
        assertTrue(forestTrees > 100);
    }

    @Test
    void densityRetainsOpenLandSparseEdgesAndDenseForest() {
        int open = countTrees(new TreeGenerator(42, (x, z) -> -.1, FLAT_GRASS), -3, 3);
        int edge = countTrees(new TreeGenerator(42, (x, z) -> 0, FLAT_GRASS), -3, 3);
        int forest = countTrees(new TreeGenerator(42, (x, z) -> .4, FLAT_GRASS), -3, 3);
        assertEquals(0, open);
        assertTrue(edge > 0);
        assertTrue(forest > 3 * edge);
    }

    @Test
    void denseForestDoesNotPutRootsOnShoreRockOrSnow() {
        for (byte material : new byte[] {BlockId.SAND, BlockId.GRAVEL, BlockId.STONE, BlockId.SNOW}) {
            TreeGenerator generator = new TreeGenerator(42, (x, z) -> 1, terrain((x, z) -> 8, material));
            assertEquals(0, countTrees(generator, -3, 3));
        }
    }

    @Test
    void treesAvoidSteepSurfaceChanges() {
        TerrainSource terrain = terrain((x, z) -> x < 0 ? 8 : 30);
        TreeGenerator generator = new TreeGenerator(0, (x, z) -> 1, terrain);
        boolean leftTree = false, rightTree = false;
        for (int chunkX = -1; chunkX <= 0; chunkX++)
            for (int chunkY = 0; chunkY <= 2; chunkY++) {
                int originX = chunkX * Chunk.SIZE, originY = chunkY * Chunk.SIZE;
                byte[] blocks = generate(generator, originX, originY, 0);
                for (int z = 0; z < Chunk.SIZE; z++)
                    for (int x = 0; x < Chunk.SIZE; x++) {
                        int worldX = originX + x, worldZ = z;
                        int y = terrain.surfaceHeight(worldX, worldZ) + 1 - originY;
                        if (y < 0 || y >= Chunk.SIZE || blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] != BlockId.LOG)
                            continue;
                        int height = terrain.surfaceHeight(worldX, worldZ);
                        assertTrue(Math.abs(height - terrain.surfaceHeight(worldX - 2, worldZ)) <= 2);
                        assertTrue(Math.abs(height - terrain.surfaceHeight(worldX + 2, worldZ)) <= 2);
                        leftTree |= worldX < 0;
                        rightTree |= worldX >= 0;
                    }
            }
        assertTrue(leftTree);
        assertTrue(rightTree);
    }

    private static int countTrees(TreeGenerator generator, int firstChunkX, int lastChunkX) {
        int trees = 0;
        for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++)
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                byte[] blocks = generate(generator, chunkX * Chunk.SIZE, 0, chunkZ * Chunk.SIZE);
                for (int z = 0; z < Chunk.SIZE; z++)
                    for (int x = 0; x < Chunk.SIZE; x++)
                        if (blocks[(9 * Chunk.SIZE + z) * Chunk.SIZE + x] == BlockId.LOG) trees++;
            }
        return trees;
    }

    private static boolean hasLeavesNearEdge(byte[] blocks, int firstX, int lastX) {
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = firstX; x <= lastX; x++)
                    if (blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] == BlockId.LEAVES) return true;
        return false;
    }

    private static TerrainSource terrain(HeightField heightField) {
        return terrain(heightField, BlockId.GRASS);
    }

    private static TerrainSource terrain(HeightField heightField, byte material) {
        return new TerrainSource() {
            @Override
            public long seed() {
                return 0;
            }

            @Override
            public int generatorVersion() {
                return 0;
            }

            @Override
            public ChunkData generate(ChunkPos position) {
                return new ChunkData(position, new byte[ChunkData.BLOCK_COUNT]);
            }

            @Override
            public int surfaceHeight(int worldX, int worldZ) {
                return heightField.sample(worldX, worldZ);
            }

            @Override
            public TerrainSummary sample(int worldX, int worldZ) {
                return new TerrainSummary(heightField.sample(worldX, worldZ), material, BlockId.DIRT, BlockId.STONE);
            }
        };
    }

    @FunctionalInterface
    private interface HeightField {
        int sample(int worldX, int worldZ);
    }

    private static byte[] generate(TreeGenerator generator, int x, int y, int z) {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        generator.generate(x, y, z, blocks);
        return blocks;
    }

    private static boolean has(byte[] blocks, byte block) {
        for (byte value : blocks) if (value == block) return true;
        return false;
    }
}
