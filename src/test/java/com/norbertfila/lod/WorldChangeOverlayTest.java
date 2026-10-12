package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WaterState;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldChangeOverlayTest {
    @Test
    void replaysStateOnlyDeltasAndExportsAnImmutableStateSnapshot() {
        WorldChangeOverlay overlay = new WorldChangeOverlay();
        BlockPos position = new BlockPos(-1, -1, -1);
        Chunk target = new Chunk();
        target.set(15, 15, 15, BlockId.WATER);
        overlay.set(position, BlockId.WATER, WaterState.flowing(3), 64);
        var captured = overlay.changes();
        overlay.set(position, BlockId.WATER, WaterState.FALLING, 64);
        overlay.applyTo(new ChunkPos(-1, -1, -1), target);

        assertEquals(WaterState.FALLING, target.waterState(15, 15, 15));
        assertEquals(2, target.revision());
        assertEquals(WaterState.flowing(3), captured.getFirst().waterState());
        overlay.applyTo(new ChunkPos(-1, -1, -1), target);
        assertEquals(2, target.revision());
        assertEquals(WaterState.FALLING, overlay.getWaterState(position).orElseThrow());
        assertEquals(BlockId.WATER, overlay.set(position, BlockId.STONE, 64).orElseThrow());
        assertEquals(WaterState.EMPTY, overlay.getWaterState(position).orElseThrow());
        overlay.applyTo(new ChunkPos(-1, -1, -1), target);
        assertEquals(WaterState.EMPTY, target.waterState(15, 15, 15));
        assertEquals(BlockId.STONE, target.get(15, 15, 15));
        assertEquals(BlockId.STONE, overlay.remove(position, 64).orElseThrow());
        assertTrue(overlay.getWaterState(position).isEmpty());
        assertTrue(overlay.changes().isEmpty());
    }

    @Test
    void invalidMetadataDoesNotReplaceAnExistingDeltaOrDirtyItsTile() {
        WorldChangeOverlay overlay = new WorldChangeOverlay();
        BlockPos position = new BlockPos(-1, -1, -1);
        overlay.set(position, BlockId.WATER, WaterState.FALLING, 64);
        overlay.drainDirtyTiles();
        assertThrows(IllegalArgumentException.class, () -> overlay.set(position, BlockId.AIR, WaterState.FALLING, 64));
        assertThrows(IllegalArgumentException.class, () -> overlay.set(position, BlockId.WATER, WaterState.EMPTY, 64));
        assertThrows(IllegalArgumentException.class, () -> overlay.set(position, BlockId.WATER, WaterState.SOURCE, 0));
        assertEquals(WaterState.FALLING, overlay.getWaterState(position).orElseThrow());
        assertEquals(1, overlay.size());
        assertTrue(overlay.dirtyTiles().isEmpty());
    }

    @Test
    void appliesOnlyTargetChunkDeltasIncludingAirAndLatestValues() {
        WorldChangeOverlay overlay = new WorldChangeOverlay();
        Chunk target = new Chunk();
        target.set(15, 4, 15, BlockId.STONE);
        overlay.set(new BlockPos(-1, -12, -1), BlockId.AIR, 8);
        BlockPos placed = new BlockPos(-15, -11, -14);
        overlay.set(placed, BlockId.STONE, 8);
        overlay.set(placed, BlockId.DIRT, 8);
        overlay.set(new BlockPos(16, -11, 16), BlockId.GRASS, 8);
        overlay.set(new BlockPos(-15, 5, -14), BlockId.GRASS, 8);
        var deltas = overlay.changes();
        overlay.drainDirtyTiles();

        overlay.applyTo(new ChunkPos(-1, -1, -1), target);

        assertEquals(BlockId.AIR, target.get(15, 4, 15));
        assertEquals(BlockId.DIRT, target.get(1, 5, 2));
        assertEquals(BlockId.AIR, target.get(0, 5, 0));
        assertEquals(3, target.revision());
        overlay.applyTo(new ChunkPos(-1, -1, -1), target);
        assertEquals(3, target.revision());
        assertEquals(deltas, overlay.changes());
        assertEquals(4, overlay.size());
        assertTrue(overlay.dirtyTiles().isEmpty());
    }

    @Test
    void removesTheLastChunkDeltaAndDoesNotReplayIt() {
        WorldChangeOverlay overlay = new WorldChangeOverlay();
        BlockPos position = new BlockPos(-1, 4, -9);
        overlay.set(position, BlockId.STONE, 8);
        overlay.remove(position, 8);
        Chunk target = new Chunk();

        overlay.applyTo(new ChunkPos(-1, 0, -1), target);

        assertEquals(0, overlay.size());
        assertTrue(overlay.get(position).isEmpty());
        assertTrue(overlay.changes().isEmpty());
        assertEquals(0, target.revision());
        assertEquals(BlockId.AIR, target.get(15, 4, 7));
    }

    @Test
    void keepsStableWorldCoordinateOrderAcrossChunkBuckets() {
        WorldChangeOverlay overlay = new WorldChangeOverlay();
        BlockPos first = new BlockPos(-17, 4, -9);
        BlockPos middle = new BlockPos(-1, 4, -9);
        BlockPos last = new BlockPos(16, 4, 16);
        overlay.set(last, BlockId.STONE, 8);
        overlay.set(first, BlockId.AIR, 8);
        overlay.set(middle, BlockId.DIRT, 8);

        assertEquals(
                List.of(first, middle, last),
                overlay.changes().stream()
                        .map(WorldChangeOverlay.Change::position)
                        .toList());
    }

    @Test
    void invalidTileSizeDoesNotPartiallyChangeIndexedDeltas() {
        WorldChangeOverlay overlay = new WorldChangeOverlay();
        BlockPos position = new BlockPos(-1, 4, -9);
        assertThrows(IllegalArgumentException.class, () -> overlay.set(position, BlockId.STONE, 0));
        assertEquals(0, overlay.size());
        overlay.set(position, BlockId.DIRT, 8);
        overlay.drainDirtyTiles();
        assertThrows(IllegalArgumentException.class, () -> overlay.remove(position, 0));
        assertEquals(BlockId.DIRT, overlay.get(position).orElseThrow());
        assertEquals(1, overlay.size());
        assertTrue(overlay.dirtyTiles().isEmpty());
    }

    @Test
    void storesOnlyOverridesAndMarksNegativeFarTilesDirty() {
        WorldChangeOverlay overlay = new WorldChangeOverlay();
        BlockPos position = new BlockPos(-1, 4, -9);

        assertTrue(overlay.set(position, BlockId.STONE, 8).isEmpty());
        assertEquals(1, overlay.size());
        assertEquals(3, overlay.get(position).orElseThrow());
        assertEquals(List.of(new WorldChangeOverlay.FarTilePos(-1, -2)), overlay.dirtyTiles());

        assertEquals(3, overlay.remove(position, 8).orElseThrow());
        assertEquals(0, overlay.size());
        assertEquals(List.of(new WorldChangeOverlay.FarTilePos(-1, -2)), overlay.drainDirtyTiles());
        assertTrue(overlay.dirtyTiles().isEmpty());
    }
}
