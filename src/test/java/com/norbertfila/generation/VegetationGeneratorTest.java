package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VegetationGeneratorTest {
    private static final TerrainSummary GRASS = new TerrainSummary(15, BlockId.GRASS, BlockId.DIRT, BlockId.STONE);

    @Test
    void snowyTaigaCoversSoilAcrossSectionBoundariesWithoutReplacingTreeTrunks() {
        var summary = new TerrainSummary(
                15,
                BlockId.PODZOL,
                BlockId.COARSE_DIRT,
                BlockId.STONE,
                TerrainSummary.NO_BEDROCK,
                TerrainSummary.NO_WATER,
                BiomeType.SNOWY_TAIGA,
                new ClimateSample(-.6f, 0),
                WaterKind.NONE,
                true);
        var column = TerrainColumn.of(columns(summary), List.of());
        var generator = new SnowGenerator();
        byte[] below = new byte[Chunk.BLOCK_BYTES];
        generator.generate(-16, 0, -16, below, column);
        assertArrayEquals(new byte[Chunk.BLOCK_BYTES], below);
        byte[] above = new byte[Chunk.BLOCK_BYTES];
        above[0] = BlockId.PINE_LOG;
        generator.generate(-16, 16, -16, above, column);
        assertEquals(BlockId.PINE_LOG, above[0]);
        int snow = 0, plants = 0, bare = 0;
        for (int i = 1; i < Chunk.SIZE * Chunk.SIZE; i++) {
            if (above[i] == BlockId.SNOW_LAYER) snow++;
            else if (above[i] == BlockId.AIR) bare++;
            else {
                assertTrue(PlantRules.supports(above[i], BlockId.PODZOL, false));
                plants++;
            }
        }
        assertEquals(255, snow);
        assertEquals(0, bare);
        assertEquals(0, plants);
        byte[] repeat = new byte[Chunk.BLOCK_BYTES];
        repeat[0] = BlockId.PINE_LOG;
        generator.generate(-16, 16, -16, repeat, column);
        assertArrayEquals(above, repeat);
    }

    @Test
    void ordinaryTaigaNeverGetsSnowFromVegetation() {
        var summary = new TerrainSummary(
                15,
                BlockId.PODZOL,
                BlockId.COARSE_DIRT,
                BlockId.STONE,
                TerrainSummary.NO_BEDROCK,
                TerrainSummary.NO_WATER,
                BiomeType.TAIGA,
                new ClimateSample(-.6f, 0));
        for (long seed : new long[] {0, 42, -1371839073867120140L}) {
            var generator = new VegetationGenerator(seed, (x, z) -> -1, (x, z) -> -1);
            int snow = 0, adjacentSnow = 0;
            byte[][] area = new byte[128][128];
            for (int z = -4; z < 4; z++)
                for (int x = -4; x < 4; x++) {
                    var position = new ChunkPos(x, 1, z);
                    byte[] blocks = generate(generator, position, summary);
                    assertArrayEquals(blocks, generate(generator, position, summary));
                    for (int lz = 0; lz < 16; lz++)
                        for (int lx = 0; lx < 16; lx++)
                            area[(z + 4) * 16 + lz][(x + 4) * 16 + lx] = blocks[lz * 16 + lx];
                }
            for (int z = 0; z < 128; z++)
                for (int x = 0; x < 127; x++) {
                    if (area[z][x] != BlockId.SNOW_LAYER) continue;
                    snow++;
                    if (area[z][x + 1] == BlockId.SNOW_LAYER) adjacentSnow++;
                }
            double coverage = snow / (128.0 * 127);
            assertEquals(0, coverage);
        }
    }

    @Test
    void taigaSnowRestsOnExposedCanopiesAcrossVerticalAndNegativeHorizontalSeams() {
        var summary = new TerrainSummary(
                6,
                BlockId.PODZOL,
                BlockId.COARSE_DIRT,
                BlockId.STONE,
                TerrainSummary.NO_BEDROCK,
                TerrainSummary.NO_WATER,
                BiomeType.SNOWY_TAIGA,
                new ClimateSample(-.6f, 0),
                WaterKind.NONE,
                true);
        var trees = new ArrayList<TreeGenerator.Tree>();
        for (int z = -18; z <= 18; z += 6)
            for (int x = -18; x <= 18; x += 6)
                trees.add(new TreeGenerator.Tree(
                        x, z, 7, 5, 2, x * 31L + z, com.norbertfila.generation.biome.TreeType.CONIFER));
        assertEquals(
                -1,
                TerrainColumn.of(columns(summary), trees).uniformBlock(16),
                "The snow above a crown must not be skipped as uniform air");
        trees.add(new TreeGenerator.Tree(-6, -6, 15, 5, 2, 42, com.norbertfila.generation.biome.TreeType.CONIFER));
        var column = TerrainColumn.of(columns(summary), trees);
        var generator = new SnowGenerator();
        int snowOnLeaves = 0, snowAtSeam = 0;
        for (int oz : new int[] {-16, 0})
            for (int ox : new int[] {-16, 0}) {
                byte[][] sections = new byte[2][Chunk.BLOCK_BYTES];
                for (int section = 1; section >= 0; section--) {
                    byte[] blocks = sections[section];
                    TreeGenerator.place(trees, ox, section * 16, oz, blocks);
                    byte[] before = blocks.clone();
                    generator.generate(ox, section * 16, oz, blocks, column);
                    byte[] repeat = before.clone();
                    generator.generate(ox, section * 16, oz, repeat, column);
                    assertArrayEquals(blocks, repeat);
                    for (int i = 0; i < blocks.length; i++)
                        if (before[i] != BlockId.AIR) assertEquals(before[i], blocks[i]);
                }
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        for (int y = 8; y < 32; y++) {
                            if (sections[y / 16][(y % 16 * 16 + z) * 16 + x] != BlockId.SNOW_LAYER) continue;
                            byte support = sections[(y - 1) / 16][((y - 1) % 16 * 16 + z) * 16 + x];
                            assertEquals(BlockId.PINE_NEEDLES, support);
                            assertTrue(PlantRules.supports(BlockId.SNOW_LAYER, support, false));
                            for (int above = y + 1; above < 32; above++)
                                assertEquals(BlockId.AIR, sections[above / 16][(above % 16 * 16 + z) * 16 + x]);
                            snowOnLeaves++;
                            if (y == 16) snowAtSeam++;
                        }
            }
        assertTrue(snowOnLeaves > 0);
        assertTrue(snowAtSeam > 0, "Snow must also appear above leaves at y=15");
    }

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
