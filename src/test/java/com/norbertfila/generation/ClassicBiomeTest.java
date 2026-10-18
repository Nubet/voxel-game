package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClassicBiomeTest {
    private static final Map<BiomeType, ClimateSample> CLIMATES = Map.of(
            BiomeType.RAINFOREST, new ClimateSample(.5f, .6f),
            BiomeType.SEASONAL_FOREST, new ClimateSample(.3f, .2f),
            BiomeType.CLASSIC_FOREST, new ClimateSample(0, .2f),
            BiomeType.SWAMPLAND, new ClimateSample(0, .6f),
            BiomeType.SAVANNA, new ClimateSample(.2f, -.3f),
            BiomeType.SHRUBLAND, new ClimateSample(0, -.2f),
            BiomeType.CLASSIC_PLAINS, new ClimateSample(0, -.6f),
            BiomeType.CLASSIC_DESERT, new ClimateSample(.6f, -.6f),
            BiomeType.TUNDRA, new ClimateSample(-.6f, .2f));

    @Test
    void classicBiomesHaveSeparateIdentitiesAndDoNotBecomeMountainBiomesAtAltitude() {
        var resolver = new BiomeResolver();
        CLIMATES.forEach((biome, climate) -> {
            assertTrue(biome.classic());
            assertEquals(biome, resolver.resolve(climate, 1));
            assertFalse(resolver.resolve(climate, 0).classic());
            for (int height : new int[] {29, 60, 160})
                assertEquals(biome, resolver.resolve(climate, height, 32, 3, false, 28, WaterKind.OCEAN, 1));
            for (var water : new WaterKind[] {WaterKind.LAKE, WaterKind.RIVER, WaterKind.OCEAN})
                assertEquals(BiomeType.valueOf(water.name()), resolver.resolve(climate, 20, 32, 0, true, 28, water, 1));
        });
        assertEquals(BiomeType.TAIGA, resolver.resolve(new ClimateSample(-.3f, .2f), 1));
    }

    @Test
    void defaultWorldActuallyContainsEveryClassicBiomeAlongsideModernBiomes() {
        var terrain = new TerrainGenerator(7340215L, TerrainConfig.defaults());
        var found = new EnumMap<BiomeType, Integer>(BiomeType.class);
        for (int z = -8192; z <= 8192; z += 128)
            for (int x = -8192; x <= 8192; x += 128) {
                var ground = terrain.sample(x, z);
                found.merge(ground.biome(), 1, Integer::sum);
                if (ground.biome() == BiomeType.TUNDRA && ground.surfaceMaterial() == BlockId.GRASS)
                    assertTrue(ground.snowCover());
                if (ground.biome() == BiomeType.CLASSIC_DESERT && ground.surfaceMaterial() == BlockId.SAND) {
                    assertEquals(BlockId.SANDSTONE, ground.subsurfaceMaterial());
                    assertNotEquals(WaterKind.RIVER, ground.waterKind());
                }
            }
        assertTrue(found.keySet().containsAll(CLIMATES.keySet()), found.toString());
        assertTrue(
                found.keySet()
                        .containsAll(Set.of(
                                BiomeType.FOREST,
                                BiomeType.PLAINS,
                                BiomeType.DESERT,
                                BiomeType.SWAMP,
                                BiomeType.JUNGLE,
                                BiomeType.BADLANDS,
                                BiomeType.TAIGA,
                                BiomeType.SNOWY_TAIGA)),
                found.toString());
    }

    @Test
    void classicForestsUseOrdinaryTreesAndHaveDistinctActualDensities() {
        var registry = BiomeRegistry.defaults();
        var landscapes = new LandscapeSampler(42, LandscapeTestConfig.uniform());
        var counts = new EnumMap<BiomeType, Integer>(BiomeType.class);
        for (var biome : CLIMATES.keySet()) {
            var ground = new TerrainSummary(
                    0,
                    BlockId.GRASS,
                    BlockId.DIRT,
                    BlockId.STONE,
                    TerrainSummary.NO_BEDROCK,
                    TerrainSummary.NO_WATER,
                    biome,
                    CLIMATES.get(biome));
            var generator = new TreeGenerator(42, (x, z) -> 1, (GroundSampler) (x, z) -> ground, registry, landscapes);
            var trees = new HashSet<TreeGenerator.Tree>();
            for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) trees.addAll(generator.trees(x * 16, z * 16));
            counts.put(biome, trees.size());
            assertTrue(trees.stream().allMatch(TreeGenerator.Tree::classicCrown));
            assertTrue(trees.stream().allMatch(t -> t.type() == TreeType.BROADLEAF || t.type() == TreeType.BIRCH));
            if (biome == BiomeType.CLASSIC_FOREST) {
                var birch = trees.stream()
                        .filter(t -> t.type() == TreeType.BIRCH)
                        .findFirst()
                        .orElseThrow();
                byte[] blocks = new byte[Chunk.BLOCK_BYTES];
                TreeGenerator.place(List.of(birch), birch.x() - 8, 0, birch.z() - 8, blocks);
                assertEquals(BlockId.BIRCH_LOG, blocks[(16 + 8) * 16 + 8]);
                assertTrue(contains(blocks, BlockId.BIRCH_LEAVES));
            }
            if (biome == BiomeType.RAINFOREST) assertTrue(trees.stream().allMatch(t -> t.trunkHeight() >= 6));
        }
        var ordered = List.of(
                BiomeType.RAINFOREST,
                BiomeType.CLASSIC_FOREST,
                BiomeType.SEASONAL_FOREST,
                BiomeType.SWAMPLAND,
                BiomeType.SAVANNA,
                BiomeType.SHRUBLAND,
                BiomeType.CLASSIC_PLAINS);
        for (int i = 1; i < ordered.size(); i++)
            assertTrue(counts.get(ordered.get(i - 1)) > counts.get(ordered.get(i)), counts.toString());
        assertEquals(0, counts.get(BiomeType.CLASSIC_DESERT));
    }

    @Test
    void classicTreesCanGrowOnSteepGrassySlopes() {
        for (var biome : List.of(BiomeType.FOREST, BiomeType.CLASSIC_FOREST)) {
            GroundSampler terrain = (x, z) -> new TerrainSummary(
                    32 + 2 * x,
                    BlockId.GRASS,
                    BlockId.DIRT,
                    BlockId.STONE,
                    TerrainSummary.NO_BEDROCK,
                    TerrainSummary.NO_WATER,
                    biome,
                    new ClimateSample(0, .2f));
            var trees = new TreeGenerator(
                            42,
                            (x, z) -> 1,
                            terrain,
                            BiomeRegistry.defaults(),
                            new LandscapeSampler(42, LandscapeTestConfig.uniform()))
                    .trees(0, 0);
            assertEquals(biome == BiomeType.FOREST, trees.isEmpty());
        }
    }

    @Test
    void classicSlopesKeepGrassAndTundraHasSnowOverSoil() {
        var registry = BiomeRegistry.defaults();
        var resolver = new SurfaceMaterialResolver(new SurfaceMaterialMapper());
        for (var biome : List.of(BiomeType.CLASSIC_FOREST, BiomeType.SWAMPLAND, BiomeType.TUNDRA)) {
            var surface = resolver.resolve(
                    160, 2.5, 28, 32, CLIMATES.get(biome), registry.profile(biome), WaterKind.OCEAN, false, 0, true);
            assertEquals(BlockId.GRASS, surface.surface());
            assertEquals(BlockId.DIRT, surface.subsurface());
        }
        assertTrue(SnowRules.covered(BiomeType.TUNDRA, -.6f, 2.5, -1));
        assertTrue(
                SnowRules.freezes(WaterKind.LAKE, CLIMATES.get(BiomeType.TUNDRA).temperature()));
        assertEquals(
                SurfaceMaterial.GRASS,
                registry.profile(BiomeType.SWAMPLAND).surface().surface());
        assertTrue(registry.profile(BiomeType.CLASSIC_PLAINS).vegetation().flowerDensity()
                > registry.profile(BiomeType.SHRUBLAND).vegetation().flowerDensity());
        assertTrue(registry.profile(BiomeType.SAVANNA).vegetation().grassDensity()
                > registry.profile(BiomeType.SHRUBLAND).vegetation().grassDensity());
    }

    @Test
    void lushClassicColorsUseTheExistingPackedPalette() {
        var climate = CLIMATES.get(BiomeType.RAINFOREST);
        int index = BiomeTint.climateIndex(climate, 1);
        assertTrue(index > 0 && index < 8);
        assertArrayEquals(BiomeTint.grass(BiomeType.RAINFOREST), BiomeTint.color(index, false), .00001f);
        assertTrue(BiomeTint.color(index, false)[1] > BiomeTint.color(BiomeTint.climateIndex(climate), false)[1]);
        for (var sample : CLIMATES.values())
            for (double classicness : new double[] {0, .25, .5, .75, 1}) {
                int tint = BiomeTint.climateIndex(sample, classicness);
                assertTrue(tint >= 0 && tint < 32);
                assertEquals(tint, BiomeTint.blend(sample, sample, sample, sample, .4, .6, classicness));
            }
    }

    private static boolean contains(byte[] blocks, byte expected) {
        for (byte block : blocks) if (block == expected) return true;
        return false;
    }
}
