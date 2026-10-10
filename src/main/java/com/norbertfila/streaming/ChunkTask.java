package com.norbertfila.streaming;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import java.util.Objects;
import java.util.concurrent.Callable;

public record ChunkTask(ChunkPos position, long token, ChunkPriority priority, Callable<MeshData> work) {
    public ChunkTask(ChunkPos position, long token, Callable<MeshData> work) {
        this(position, token, ChunkPriority.unprioritized(), work);
    }

    public ChunkTask {
        Objects.requireNonNull(position);
        Objects.requireNonNull(priority);
        Objects.requireNonNull(work);
    }

    public record Outcome(ChunkPos position, long token, MeshData mesh, Throwable failure) {
        public Outcome {
            Objects.requireNonNull(position);
            if ((mesh == null) == (failure == null)) {
                throw new IllegalArgumentException("Outcome must contain either mesh or failure");
            }
        }

        public boolean succeeded() {
            return mesh != null;
        }
    }
}
