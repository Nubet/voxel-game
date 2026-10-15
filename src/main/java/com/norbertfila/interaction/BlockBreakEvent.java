package com.norbertfila.interaction;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import java.util.Objects;

public record BlockBreakEvent(long tick, BlockPos block, int blockId) {
    public BlockBreakEvent {
        Objects.requireNonNull(block);
        if (tick < 0 || blockId <= 0 || blockId > 255 || !BlockId.isValid((byte) blockId))
            throw new IllegalArgumentException("Invalid block break event");
    }
}
