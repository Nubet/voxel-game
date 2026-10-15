package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.TerrainSummary;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class FarTerrainGeneratorTest {
    private static final int SURFACE = 10;
    private static final int SEA = 14;
    private final WorldChangeOverlay overlay = new WorldChangeOverlay();
    // Flat grass at height 10 with bedrock at 0, flooded up to 14 where x is negative.
    private final FarTerrainGenerator generator = new FarTerrainGenerator(new FlatSource());

    @Test
    void tilesAreDeterministicAndMatchTheGeneratedSurface() {
        TerrainGenerator source = new TerrainGenerator(7340215L, TerrainConfig.defaults());
        FarTerrainTile first = new FarTerrainGenerator(source).generate(-3, 5, 4, AreaEdits.none(-192, 320, 64));
        FarTerrainTile second = new FarTerrainGenerator(new TerrainGenerator(7340215L, TerrainConfig.defaults()))
                .generate(-3, 5, 4, AreaEdits.none(-192, 320, 64));

        assertEquals(16, first.cells());
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                TerrainSummary summary = source.sample(-192 + x * 4 + 2, 320 + z * 4 + 2);
                assertEquals(summary.surfaceHeight(), first.heightAt(x, z));
                assertEquals(summary.surfaceMaterial(), first.materialAt(x, z));
                assertEquals(
                        summary.flooded() ? summary.waterLevel() : FarTerrainTile.NO_WATER, first.waterTopAt(x, z));
                assertEquals(first.heightAt(x, z), second.heightAt(x, z));
                assertEquals(first.waterTopAt(x, z), second.waterTopAt(x, z));
            }
    }

    @Test
    void towerAboveTheGeneratedMaximumRaisesItsCellAndOnlyItsTile() {
        long before = overlay.areaRevision(0, 0, 64);
        for (int y = SURFACE + 1; y <= 400; y++) overlay.set(new BlockPos(5, y, 6), BlockId.BRICKS, 64);

        assertNotEquals(before, overlay.areaRevision(0, 0, 64));
        assertEquals(0, overlay.areaRevision(64, 0, 64));
        FarTerrainTile tile = tile(0, 0, 4);
        assertEquals(400, tile.heightAt(1, 1));
        assertEquals(BlockId.BRICKS, tile.materialAt(1, 1));
        assertEquals(400, tile.maxHeight());
        assertEquals(SURFACE, tile.heightAt(2, 1));
        assertEquals(SURFACE, tile(1, 0, 4).heightAt(0, 0));
    }

    @Test
    void removedPeakLowersTheCellToTheBlockBelow() {
        overlay.set(new BlockPos(2, SURFACE, 2), BlockId.AIR, 64);
        overlay.set(new BlockPos(2, SURFACE - 1, 2), BlockId.AIR, 64);

        FarTerrainTile tile = tile(0, 0, 4);

        assertEquals(SURFACE - 2, tile.heightAt(0, 0));
        assertEquals(BlockId.DIRT, tile.materialAt(0, 0));
        assertEquals(SURFACE, tile.heightAt(1, 0), "digging off the sampled column is not shown");
    }

    @Test
    void waterSurfaceFollowsPlacedAndRemovedWater() {
        overlay.set(new BlockPos(2, SURFACE + 1, 2), BlockId.WATER, 64);
        overlay.set(new BlockPos(-62, SEA, 2), BlockId.AIR, 64);
        overlay.set(new BlockPos(-58, SEA + 3, 2), BlockId.WATER, 64);

        assertEquals(SURFACE + 1, tile(0, 0, 4).waterTopAt(0, 0));
        FarTerrainTile sea = tile(-1, 0, 4);
        assertEquals(SEA - 1, sea.waterTopAt(0, 0));
        assertEquals(SEA + 3, sea.waterTopAt(1, 0), "water above an air gap still shows");
        assertEquals(SEA, sea.waterTopAt(2, 0));
        assertEquals(FarTerrainTile.NO_WATER, tile(0, 0, 4).waterTopAt(1, 0));
    }

    @Test
    void buildingOverWaterHidesTheWaterBelowIt() {
        overlay.set(new BlockPos(-63, SEA + 2, 1), BlockId.PLANKS, 64);

        FarTerrainTile sea = tile(-1, 0, 4);

        assertEquals(SEA + 2, sea.heightAt(0, 0));
        assertEquals(FarTerrainTile.NO_WATER, sea.waterTopAt(0, 0));
    }

    @Test
    void heightfieldKeepsOnlyTheTopmostSurface() {
        for (int y = 3; y < SURFACE; y++) overlay.set(new BlockPos(2, y, 2), BlockId.AIR, 64);
        overlay.set(new BlockPos(6, 30, 6), BlockId.STONE, 64);
        overlay.set(new BlockPos(10, SURFACE + 1, 10), BlockId.SHORT_GRASS, 64);

        FarTerrainTile tile = tile(0, 0, 4);

        assertEquals(SURFACE, tile.heightAt(0, 0), "a cave under the surface is invisible");
        assertEquals(30, tile.heightAt(1, 1), "an overhang hides everything below it");
        assertEquals(SURFACE, tile.heightAt(2, 2), "plants are not surface");
    }

    @Test
    void shaftThroughBedrockLeavesNoSurface() {
        for (int y = 0; y <= SURFACE; y++) overlay.set(new BlockPos(0, y, 0), BlockId.AIR, 64);

        FarTerrainTile tile = tile(0, 0, 1);

        assertEquals(FarTerrainTile.NO_SURFACE, tile.heightAt(0, 0));
        assertEquals(SURFACE, tile.heightAt(1, 0));
    }

    @Test
    void negativeTilesUseFloorDivisionAndFullDetailMatchesEveryColumn() {
        overlay.set(new BlockPos(-1, SURFACE + 5, -1), BlockId.STONE, 64);

        FarTerrainTile tile = tile(-1, -1, 1);

        assertEquals(-64, tile.originX());
        assertEquals(64, tile.cells());
        assertEquals(SURFACE + 5, tile.heightAt(63, 63));
        assertEquals(SURFACE, tile.heightAt(62, 63));
        assertTrue(tile.editRevision() > 0);
    }

    @Test
    void editRevisionGrowsWhenAChangeIsRemoved() {
        BlockPos position = new BlockPos(20, SURFACE + 1, 20);
        overlay.set(position, BlockId.STONE, 64);
        long placed = overlay.areaRevision(0, 0, 64);
        overlay.remove(position, 64);

        assertTrue(overlay.areaRevision(0, 0, 64) > placed);
        assertEquals(SURFACE, tile(0, 0, 4).heightAt(5, 5));
        assertEquals(0, overlay.edits(0, 0, 64).columnCount());
    }

    @Test
    void rejectsEditsForAnotherArea() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(0, 0, 4, AreaEdits.none(64, 0, 64)));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(0, 0, 3, AreaEdits.none(0, 0, 64)));
    }

    @Test
    void editSnapshotIsUnaffectedByLaterChanges() {
        overlay.set(new BlockPos(1, 50, 1), BlockId.STONE, 64);
        AreaEdits snapshot = overlay.edits(0, 0, 64);
        overlay.set(new BlockPos(1, 60, 1), BlockId.STONE, 64);

        assertArrayEquals(new int[] {50}, new int[] {snapshot.changeY(snapshot.find(1, 1), 0)});
        assertEquals(1, snapshot.changeCount(0));
    }

    private FarTerrainTile tile(int tileX, int tileZ, int step) {
        return generator.generate(
                tileX, tileZ, step, overlay.edits(tileX * FarTerrainTile.SIZE, tileZ * FarTerrainTile.SIZE, 64));
    }

    private static final class FlatSource implements TerrainSource {
        @Override
        public int surfaceHeight(int worldX, int worldZ) {
            return SURFACE;
        }

        @Override
        public TerrainSummary sample(int worldX, int worldZ) {
            return new TerrainSummary(
                    SURFACE, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, 0, worldX < 0 ? SEA : TerrainSummary.NO_WATER);
        }

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
            throw new AssertionError("far terrain must not generate chunks");
        }
    }
}
