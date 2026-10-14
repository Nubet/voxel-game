package com.norbertfila.persistence.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldSnapshotsTest {
    @Test
    void detachedDeltasRestoreIntoLaterAdmittedChunksWithNegativeCoordinates() {
        var original = new World();
        var position = new BlockPos(-17, -1, -9);
        original.setBlock(position, BlockId.WATER, WaterState.FALLING, 64);
        var snapshot = WorldSnapshots.capture(
                new WorldIdentity(UUID.randomUUID(), -42, "default", 11),
                3,
                original,
                FluidSnapshot.EMPTY,
                InventorySnapshot.EMPTY,
                new SavedPlayerState(.5, 1, .5, 0, 0, Mode.SURVIVAL, false));
        original.setBlock(position, BlockId.AIR, 64);
        var restored = new World();
        WorldSnapshots.restore(restored, snapshot, 64);
        restored.putChunk(Coordinates.worldToChunk(position), new Chunk());
        assertEquals(BlockId.WATER, restored.getBlock(position).orElseThrow());
        assertEquals(WaterState.FALLING, restored.getWaterState(position).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshots.restore(new World(), snapshot, 0));
    }
}
