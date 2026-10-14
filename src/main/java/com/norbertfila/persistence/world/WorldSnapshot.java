package com.norbertfila.persistence.world;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.world.ChunkPos;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WorldSnapshot(
        WorldIdentity identity,
        long sequence,
        Instant lastSavedAt,
        List<ChunkDelta> deltas,
        FluidSnapshot fluids,
        InventorySnapshot inventory,
        Optional<SavedPlayerState> player) {
    public WorldSnapshot {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(lastSavedAt);
        Objects.requireNonNull(fluids);
        Objects.requireNonNull(inventory);
        Objects.requireNonNull(player);
        if (sequence < 0 || lastSavedAt.isBefore(Instant.EPOCH))
            throw new IllegalArgumentException("Invalid snapshot sequence/timestamp");
        lastSavedAt = Instant.ofEpochMilli(lastSavedAt.toEpochMilli());
        deltas = List.copyOf(deltas);
        var positions = new HashSet<ChunkPos>();
        for (var delta : deltas)
            if (!positions.add(delta.position())) throw new IllegalArgumentException("Duplicate delta chunk");
        if (player.isEmpty()
                && (sequence != 0
                        || !deltas.isEmpty()
                        || !fluids.equals(FluidSnapshot.EMPTY)
                        || !inventory.equals(InventorySnapshot.EMPTY)))
            throw new IllegalArgumentException("An unstarted world must have an empty initial snapshot");
    }

    public static WorldSnapshot initial(WorldIdentity identity, Instant createdAt) {
        return new WorldSnapshot(
                identity, 0, createdAt, List.of(), FluidSnapshot.EMPTY, InventorySnapshot.EMPTY, Optional.empty());
    }
}
