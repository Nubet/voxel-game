package com.norbertfila.streaming;

public record WorkerCounts(int generation, int meshing, int far) {
    public static final WorkerCounts DEFAULT = new WorkerCounts(2, 2, 2);

    public WorkerCounts {
        if (generation < 1 || generation > 64 || meshing < 1 || meshing > 64 || far < 1 || far > 64)
            throw new IllegalArgumentException("Worker counts must be between 1 and 64");
    }
}
