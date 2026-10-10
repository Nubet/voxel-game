package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldChangeOverlayTest {
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
