package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.TerrainSummary;
import com.norbertfila.generation.WaterKind;
import com.norbertfila.generation.biome.BadlandsSurface;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class BadlandsFarTerrainTest {
    private final TerrainSource source = new BadlandsSource();
    private final FarTerrainGenerator generator = new FarTerrainGenerator(source);

    @Test
    void distantCliffsMatchEveryGeneratedLayerAtEachLodStep() {
        for (int step : new int[] {1, 4, 8, 16}) {
            var tile = generator.generate(0, 0, step, AreaEdits.none(0, 0, 64));
            var mesh = FarTileMesher.mesh(tile, generator.border(0, 0, step));
            int[][] coverage = new int[52][tile.cells()];
            int walls = 0;
            int[] records = mesh.records();
            for (int i = 0; i < records.length; i += 3) {
                int shape = records[i], extent = records[i + 2];
                if ((shape >>> 14 & 7) != FarTileMesher.FACE_POSITIVE_X || (shape & 127) != 32) continue;
                walls++;
                int z = shape >>> 7 & 127;
                int width = (shape >>> 18 & 63) + 1;
                int bottom = records[i + 1], top = bottom + (extent & 65535) + 1;
                for (int cellZ = z / step; cellZ < (z + width) / step; cellZ++) {
                    var terrain = source.sample(32 - step + step / 2, cellZ * step + step / 2);
                    for (int y = bottom; y < top; y++) {
                        assertEquals(
                                BlockRegistry.DEFAULT
                                        .definition(terrain.materialAt(y))
                                        .sideTexture(),
                                extent >>> 16 & 255);
                        coverage[y - 49][cellZ]++;
                    }
                }
            }
            assertTrue(walls > 1);
            assertTrue(walls < 52 * tile.cells());
            for (int[] row : coverage) for (int count : row) assertEquals(1, count);
        }
    }

    @Test
    void editedBlocksAndTowersKeepTheirMaterialsAboveNaturalBands() {
        var overlay = new WorldChangeOverlay();
        overlay.set(new BlockPos(2, 110, 2), BlockId.BRICKS, 64);
        overlay.set(new BlockPos(2, 98, 2), BlockId.COBBLE, 64);
        overlay.set(new BlockPos(1, 112, 5), BlockId.PLANKS, 64);
        var tile = generator.generate(0, 0, 4, overlay.edits(0, 0, 64));
        assertEquals(110, tile.heightAt(0, 0));
        assertEquals(BlockId.BRICKS, tile.wallMaterialAt(0, 0, 110));
        assertEquals(BlockId.BRICKS, tile.wallMaterialAt(0, 0, 105));
        assertEquals(BlockId.COBBLE, tile.wallMaterialAt(0, 0, 98));
        assertEquals(source.sample(2, 2).materialAt(97), tile.wallMaterialAt(0, 0, 97));
        assertEquals(112, tile.heightAt(0, 1));
        assertEquals(BlockId.PLANKS, tile.wallMaterialAt(0, 1, 112));
        assertEquals(source.sample(2, 6).materialAt(97), tile.wallMaterialAt(0, 1, 97));
        overlay.set(new BlockPos(2, 98, 2), BlockId.STONE, 64);
        assertEquals(BlockId.COBBLE, tile.wallMaterialAt(0, 0, 98));
    }

    @Test
    void diggingExposesTheSameMaterialAsTheColumnAtNegativeCoordinates() {
        var overlay = new WorldChangeOverlay();
        overlay.set(new BlockPos(-2, 100, -2), BlockId.AIR, 64);
        var tile = generator.generate(-1, -1, 4, overlay.edits(-64, -64, 64));
        assertEquals(99, tile.heightAt(15, 15));
        assertEquals(source.sample(-2, -2).materialAt(99), tile.materialAt(15, 15));
        assertEquals(source.sample(-2, -2).materialAt(90), tile.wallMaterialAt(15, 15, 90));
    }

    private static final class BadlandsSource implements TerrainSource {
        private final BadlandsSurface surface = new BadlandsSurface(42);

        @Override
        public int surfaceHeight(int x, int z) {
            return x < 32 ? 100 : 48;
        }

        @Override
        public TerrainSummary sample(int x, int z) {
            int height = surfaceHeight(x, z);
            var layers = surface.column(x, z);
            byte top = layers.surfaceAt(height, 28);
            return new TerrainSummary(
                    height,
                    top,
                    layers.materialAt(height - 1, height, top),
                    BlockId.STONE,
                    -64,
                    TerrainSummary.NO_WATER,
                    BiomeType.BADLANDS,
                    new ClimateSample(.5f, 0),
                    WaterKind.NONE,
                    false,
                    false,
                    layers);
        }

        @Override
        public long seed() {
            return 42;
        }

        @Override
        public int generatorVersion() {
            return 0;
        }

        @Override
        public ChunkData generate(ChunkPos position) {
            throw new AssertionError("Far terrain must not generate chunks");
        }
    }
}
