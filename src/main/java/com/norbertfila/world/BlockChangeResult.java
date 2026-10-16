package com.norbertfila.world;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record BlockChangeResult(Status status, OptionalInt previous, List<ChunkPos> dirtyChunks, long chunkRevision) {
    public enum Status {
        CHANGED,
        UNCHANGED,
        UNAVAILABLE,
        OUT_OF_BOUNDS
    }

    public BlockChangeResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(previous);
        dirtyChunks = List.copyOf(dirtyChunks);
    }

    public boolean changed() {
        return status == Status.CHANGED;
    }
}
