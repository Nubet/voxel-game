package com.norbertfila.vegetation;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.util.Set;

public record GrowthSite(Status status, BlockPos target, Set<ChunkPos> dependencies) {
    public enum Status {
        READY,
        MATURE,
        BLOCKED,
        INVALID,
        UNAVAILABLE
    }

    public GrowthSite {
        dependencies = Set.copyOf(dependencies);
    }
}
