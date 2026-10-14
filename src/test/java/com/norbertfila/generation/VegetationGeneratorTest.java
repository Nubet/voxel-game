package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VegetationGeneratorTest {
    private static final TerrainSummary GRASS = new TerrainSummary(15, BlockId.GRASS, BlockId.DIRT, BlockId.STONE);

    @Test
    void allFiveSpeciesAreDeterministicAcrossNegativeSeamsAndParallelGeneration() {
        var generator = new VegetationGenerator(42, (x, z) -> 1, (x, z) -> 1);
        var positions = new ArrayList<ChunkPos>();
        for (int z = -4; z <= 4; z++) for (int x = -4; x <= 4; x++) positions.add(new ChunkPos(x, 1, z));
        var expected =
                positions.stream().map(p -> generate(generator, p, GRASS)).toList();
        var parallel = positions.parallelStream()
                .map(p -> generate(generator, p, GRASS))
                .toList();
        Set<Byte> species = new HashSet<>();
        int flowersAtSeams = 0;
        for (int i = positions.size() - 1; i >= 0; i--) {
            byte[] blocks = expected.get(i);
            assertArrayEquals(blocks, parallel.get(i));
            assertArrayEquals(blocks, generate(generator, positions.get(i), GRASS));
            for (int index = 0; index < blocks.length; index++) {
                if (blocks[index] == BlockId.AIR) continue;
                assertTrue(index < 256, "Plants belong in the section above y=15");
                species.add(blocks[index]);
                if (blocks[index] != BlockId.SHORT_GRASS
                        && blocks[index] != BlockId.FERN
                        && (index % 16 == 0 || index % 16 == 15)) flowersAtSeams++;
            }
        }
        assertEquals(
                Set.of(
                        BlockId.SHORT_GRASS,
                        BlockId.FERN,
                        BlockId.DANDELION_WHITE,
                        BlockId.DANDELION_YELLOW,
                        BlockId.POPPY),
                species);
        assertTrue(flowersAtSeams > 0);
        assertTrue(Arrays.equals(new byte[4096], generate(generator, new ChunkPos(0, 0, 0), GRASS)));
    }

    @Test
    void decorationsRespectWaterGroundAndTreeOccupancy() {
        var generator = new VegetationGenerator(42, (x, z) -> 1, (x, z) -> 1);
        var position = new ChunkPos(0, 1, 0);
        for (TerrainSummary invalid : List.of(
                new TerrainSummary(15, BlockId.SAND, BlockId.SAND, BlockId.STONE),
                new TerrainSummary(15, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, Integer.MIN_VALUE, 16)))
            assertArrayEquals(new byte[4096], generate(generator, position, invalid));
        byte[] occupied = new byte[4096];
        Arrays.fill(occupied, 0, 128, BlockId.LOG);
        Arrays.fill(occupied, 128, 256, BlockId.LEAVES);
        byte[] before = occupied.clone();
        generator.generate(0, 16, 0, occupied, TerrainColumn.of(columns(GRASS), java.util.List.of()));
        assertArrayEquals(before, occupied);
    }

    @Test
    void grassDensityCreatesOpenAndDenseAreasWithoutChangingFlowerPatches() {
        var sparse = new VegetationGenerator(42, (x, z) -> -1, (x, z) -> -1);
        var dense = new VegetationGenerator(42, (x, z) -> 1, (x, z) -> -1);
        int count = 0;
        for (int z = -2; z <= 2; z++)
            for (int x = -2; x <= 2; x++) {
                var pos = new ChunkPos(x, 1, z);
                byte[] a = generate(sparse, pos, GRASS), b = generate(dense, pos, GRASS);
                for (int i = 0; i < a.length; i++) {
                    assertNotEquals(BlockId.SHORT_GRASS, a[i]);
                    if (a[i] != BlockId.AIR) assertEquals(a[i], b[i]);
                    if (b[i] == BlockId.SHORT_GRASS) count++;
                }
            }
        assertTrue(count > 1000);
    }

    static TerrainSummary[] columns(TerrainSummary summary) {
        TerrainSummary[] columns = new TerrainSummary[256];
        Arrays.fill(columns, summary);
        return columns;
    }

    private static byte[] generate(VegetationGenerator generator, ChunkPos position, TerrainSummary summary) {
        byte[] blocks = new byte[4096];
        generator.generate(
                position.x() * 16,
                position.y() * 16,
                position.z() * 16,
                blocks,
                TerrainColumn.of(columns(summary), java.util.List.of()));
        return blocks;
    }
}
