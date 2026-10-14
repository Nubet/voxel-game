package com.norbertfila.persistence.world;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

public final class WorldIo implements AutoCloseable {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), task -> new Thread(task, "voxel-world-io"));
    private final Writer writer;

    public WorldIo() {
        this(WorldStateCodec::write);
    }

    public WorldIo(Writer writer) {
        this.writer = Objects.requireNonNull(writer);
    }

    public CompletableFuture<List<WorldEntry>> list(WorldCatalog catalog) {
        return submit(catalog::list);
    }

    public CompletableFuture<WorldEntry> create(WorldCatalog catalog, WorldMetadata metadata) {
        return submit(() -> catalog.create(metadata));
    }

    public CompletableFuture<WorldCatalog.Loaded> open(WorldCatalog catalog, UUID id) {
        return submit(() -> catalog.open(id));
    }

    public CompletableFuture<WorldSnapshot> read(Path path) {
        return submit(() ->
                WorldStateCodec.read(path, WorldStateCodec.readHeader(path).identity()));
    }

    public CompletableFuture<Void> write(Path path, WorldSnapshot snapshot) {
        return submit(() -> {
            var header = WorldStateCodec.readHeader(path);
            header.requireIdentity(snapshot.identity());
            if (header.sequence() == snapshot.sequence()) {
                if (!WorldStateCodec.read(path, snapshot.identity()).equals(snapshot))
                    throw new IOException("Conflicting snapshot sequence");
            } else {
                if (header.sequence() > snapshot.sequence()) throw new IOException("Stale snapshot sequence");
                writer.write(path, snapshot);
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(Callable<T> operation) {
        var result = new CompletableFuture<T>();
        try {
            worker.execute(() -> {
                try {
                    result.complete(operation.call());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException full) {
            result.completeExceptionally(full);
        }
        return result;
    }

    @Override
    public void close() {
        worker.shutdown();
        boolean interrupted = false;
        while (!worker.isTerminated()) {
            try {
                worker.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    @FunctionalInterface
    public interface Writer {
        void write(Path path, WorldSnapshot snapshot) throws IOException;
    }
}
