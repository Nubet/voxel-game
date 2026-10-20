package com.norbertfila.vegetation;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.WorldBounds;
import java.util.Objects;

public record PlantGrowthState(BlockPos root, byte blockId, long startedAt, long nextGrowthTick, long cycle) {
    public PlantGrowthState {
        Objects.requireNonNull(root);
        if (!WorldBounds.containsY(root.y())
                || blockId != com.norbertfila.world.BlockId.REED
                || startedAt < 0
                || nextGrowthTick <= startedAt
                || cycle < 0) throw new IllegalArgumentException("Invalid plant growth state");
    }
}
