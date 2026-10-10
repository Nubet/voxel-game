package com.norbertfila.streaming;

public enum ChunkState {
    REQUESTED,
    GENERATING,
    GENERATED,
    MESHING,
    READY_CPU,
    RESIDENT_GPU,
    EVICTING
}
