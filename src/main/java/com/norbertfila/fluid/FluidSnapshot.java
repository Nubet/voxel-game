package com.norbertfila.fluid;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record FluidSnapshot(long tick, List<Update> updates) {
    public static final FluidSnapshot EMPTY = new FluidSnapshot(0, List.of());
    public static final int MAX_UPDATES = 1_000_000;

    public FluidSnapshot {
        if (tick < 0 || tick > Long.MAX_VALUE - FluidScheduler.WATER_TICK_DELAY || updates.size() > MAX_UPDATES)
            throw new IllegalArgumentException("Invalid fluid snapshot");
        updates = List.copyOf(updates);
        var positions = new HashSet<BlockPos>();
        for (Update update : updates) {
            if (!positions.add(update.position())) throw new IllegalArgumentException("Duplicate fluid update");
            Math.addExact(tick, update.remainingTicks());
        }
    }

    public record Update(BlockPos position, long remainingTicks, List<ChunkPos> missingChunks) {
        public Update {
            Objects.requireNonNull(position);
            if (remainingTicks < 0 || remainingTicks > FluidScheduler.WATER_TICK_DELAY || missingChunks.size() > 16)
                throw new IllegalArgumentException("Invalid deferred fluid update");
            missingChunks = List.copyOf(missingChunks);
            if (new HashSet<>(missingChunks).size() != missingChunks.size())
                throw new IllegalArgumentException("Duplicate missing chunk");
        }
    }
}
