package com.norbertfila.persistence;

import com.norbertfila.persistence.world.WorldIo;
import com.norbertfila.persistence.world.WorldSnapshot;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SaveScheduler {
    private static final Logger LOG = Logger.getLogger(SaveScheduler.class.getName());
    private final WorldIo io;
    private final Path target;
    private WorldSnapshot pending, active;
    private CompletableFuture<Void> work;
    private Throwable failure;
    private long savedRevision = -1, started, workerNanos;

    public SaveScheduler(WorldIo io, Path target) {
        this.io = Objects.requireNonNull(io);
        this.target = Objects.requireNonNull(target);
    }

    public synchronized void request(WorldSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        long latest = Math.max(
                savedRevision,
                Math.max(active == null ? -1 : active.sequence(), pending == null ? -1 : pending.sequence()));
        if (snapshot.sequence() <= latest) return;
        pending = snapshot;
        poll();
    }

    public synchronized void poll() {
        if (work != null && work.isDone()) {
            try {
                work.join();
                savedRevision = active.sequence();
            } catch (CompletionException failed) {
                failure = failed.getCause();
                if (pending == null || pending.sequence() < active.sequence()) pending = active;
                LOG.log(Level.WARNING, "World save failed; snapshot retained at " + target, failure);
            }
            workerNanos = System.nanoTime() - started;
            active = null;
            work = null;
        }
        if (work == null && pending != null && failure == null) {
            active = pending;
            pending = null;
            started = System.nanoTime();
            work = io.write(target, active);
        }
    }

    public synchronized void retry() {
        if (failure == null) return;
        failure = null;
        poll();
    }

    public synchronized boolean isIdle() {
        poll();
        return pending == null && work == null;
    }

    public synchronized Throwable failure() {
        poll();
        return failure;
    }

    public synchronized WorldSnapshot retained() {
        return pending != null ? pending : active;
    }

    public synchronized Metrics metrics() {
        return new Metrics(savedRevision, workerNanos, failure != null);
    }

    public record Metrics(long savedRevision, long workerNanos, boolean failed) {}
}
