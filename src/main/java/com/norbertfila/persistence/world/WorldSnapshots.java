package com.norbertfila.persistence.world;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.persistence.ChunkDelta;
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
        var grouped = new TreeMap<ChunkPos, List<ChunkDelta.BlockChange>>(Comparator.comparingInt(ChunkPos::x)
                .thenComparingInt(ChunkPos::y)
                .thenComparingInt(ChunkPos::z));
        for (var change : world.changes().changes()) {
            var chunk = Coordinates.worldToChunk(change.position());
            grouped.computeIfAbsent(chunk, ignored -> new ArrayList<>())
                    .add(new ChunkDelta.BlockChange(change.position(), change.value(), change.waterState()));
        }
        var deltas = grouped.entrySet().stream()
                .map(e -> new ChunkDelta(e.getKey(), e.getValue()))
                .toList();
        return new WorldSnapshot(identity, sequence, Instant.now(), deltas, fluids, inventory, Optional.of(player));
    }

    public static void restore(World world, WorldSnapshot snapshot, int farTileSize) {
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        for (var delta : snapshot.deltas())
            for (var change : delta.changes())
                world.setBlock(change.position(), change.value(), change.waterState(), farTileSize);
    }
}
