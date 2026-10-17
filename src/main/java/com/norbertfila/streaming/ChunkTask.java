package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.util.Objects;
import java.util.concurrent.Callable;

public record ChunkTask<T>(
        ChunkPos position,
        long revision,
        long meshDependencyRevision,
        long token,
        ChunkPriority priority,
        Callable<T> work) {
    public ChunkTask(ChunkPos position, long revision, long token, ChunkPriority priority, Callable<T> work) {
        this(position, revision, -1, token, priority, work);
    }

    public ChunkTask(ChunkPos position, long token, Callable<T> work) {
        this(position, -1, token, ChunkPriority.unprioritized(), work);
    }

    public ChunkTask(ChunkPos position, long token, ChunkPriority priority, Callable<T> work) {
        this(position, -1, token, priority, work);
    }

    public ChunkTask(ChunkPos position, long revision, long token, Callable<T> work) {
        this(position, revision, token, ChunkPriority.unprioritized(), work);
    }

    public ChunkTask {
        Objects.requireNonNull(position);
        Objects.requireNonNull(priority);
        Objects.requireNonNull(work);
    }

    public static final class Outcome<T> implements AutoCloseable {
        private final ChunkPos position;
        private final long revision;
        private final long meshDependencyRevision;
        private final long token;
        private T result;
        private final Throwable failure;
        private final long completedAt = System.nanoTime();

        public long completedAt() {
            return completedAt;
        }

        public Outcome(
                ChunkPos position,
                long revision,
                long meshDependencyRevision,
                long token,
                T result,
                Throwable failure) {
            Objects.requireNonNull(position);
            if ((result == null) == (failure == null)) {
                throw new IllegalArgumentException("Outcome must contain either a result or failure");
            }
            this.position = position;
            this.revision = revision;
            this.meshDependencyRevision = meshDependencyRevision;
            this.token = token;
            this.result = result;
            this.failure = failure;
        }

        public ChunkPos position() {
            return position;
        }

        public long revision() {
            return revision;
        }

        public long meshDependencyRevision() {
            return meshDependencyRevision;
        }

        public long token() {
            return token;
        }

        public T result() {
            return result;
        }

        public <R> R mesh() {
            @SuppressWarnings("unchecked")
            R mesh = (R) result;
            return mesh;
        }

        public Throwable failure() {
            return failure;
        }

        public boolean succeeded() {
            return result != null;
        }

        public void discard() {
            result = null;
        }

        @Override
        public void close() {
            discard();
        }
    }
}
