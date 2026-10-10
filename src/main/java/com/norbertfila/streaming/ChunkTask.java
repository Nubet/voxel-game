package com.norbertfila.streaming;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import java.util.Objects;
import java.util.concurrent.Callable;

public record ChunkTask(ChunkPos position, long revision, long token, ChunkPriority priority, Callable<MeshData> work) {
    public ChunkTask(ChunkPos position, long token, Callable<MeshData> work) {
        this(position, -1, token, ChunkPriority.unprioritized(), work);
    }

    public ChunkTask(ChunkPos position, long token, ChunkPriority priority, Callable<MeshData> work) {
        this(position, -1, token, priority, work);
    }

    public ChunkTask(ChunkPos position, long revision, long token, Callable<MeshData> work) {
        this(position, revision, token, ChunkPriority.unprioritized(), work);
    }

    public ChunkTask {
        Objects.requireNonNull(position);
        Objects.requireNonNull(priority);
        Objects.requireNonNull(work);
    }

    public static final class Outcome implements AutoCloseable {
        private final ChunkPos position;
        private final long revision;
        private final long token;
        private MeshData mesh;
        private final Throwable failure;

        public Outcome(ChunkPos position, long revision, long token, MeshData mesh, Throwable failure) {
            Objects.requireNonNull(position);
            if ((mesh == null) == (failure == null)) {
                throw new IllegalArgumentException("Outcome must contain either mesh or failure");
            }
            this.position = position;
            this.revision = revision;
            this.token = token;
            this.mesh = mesh;
            this.failure = failure;
        }

        public ChunkPos position() {
            return position;
        }

        public long revision() {
            return revision;
        }

        public long token() {
            return token;
        }

        public MeshData mesh() {
            return mesh;
        }

        public Throwable failure() {
            return failure;
        }

        public boolean succeeded() {
            return mesh != null;
        }

        public void discard() {
            mesh = null;
        }

        @Override
        public void close() {
            discard();
        }
    }
}
