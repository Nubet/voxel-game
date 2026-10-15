package com.norbertfila.persistence.world;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.world.*;
import java.time.Instant;
import java.util.*;

public final class WorldSnapshots {
    private WorldSnapshots() {}

    public static WorldSnapshot capture(
            WorldIdentity identity,
            long sequence,
            World world,
            FluidSnapshot fluids,
            InventorySnapshot inventory,
            SavedPlayerState player) {
        var deltas = world.changes().deltas();
        return new WorldSnapshot(identity, sequence, Instant.now(), deltas, fluids, inventory, Optional.of(player));
    }

    public static void restore(World world, WorldSnapshot snapshot, int farTileSize) {
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        for (var delta : snapshot.deltas())
            for (int index = 0; index < delta.size(); index++)
                world.setBlock(delta.blockPosition(index), delta.value(index), delta.waterState(index), farTileSize);
    }
}
