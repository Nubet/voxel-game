package com.norbertfila.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class SaveScheduler implements AutoCloseable {
    private final Path target;
    private final long debounceMillis;
    private final Thread worker;
    private WorldSave.Snapshot pending;
    private long pendingRevision = -1;
    private long pendingAtNanos;
    private long dirtyRevision = -1;
    private long savedRevision = -1;
    private IOException failure;
    private boolean closing;
    private long workerNanos, savedBytes;

    public synchronized Metrics metrics() {
        return new Metrics(savedRevision, workerNanos, savedBytes, failure != null);
    }

    public record Metrics(long savedRevision, long workerNanos, long savedBytes, boolean failed) {}

    public SaveScheduler(Path target, long debounceMillis) {
        this.target = Objects.requireNonNull(target);
        if (debounceMillis < 0) throw new IllegalArgumentException("Debounce must not be negative");
        this.debounceMillis = debounceMillis;
        worker = new Thread(this::run, "voxel-save-worker");
        worker.start();
    }

    public synchronized void request(WorldSave.Snapshot snapshot, long revision) {
        Objects.requireNonNull(snapshot);
        if (revision < 0) throw new IllegalArgumentException("Revision must not be negative");
        if (revision < dirtyRevision) return;
        pending = snapshot;
        pendingRevision = revision;
        dirtyRevision = revision;
        pendingAtNanos = System.nanoTime();
        failure = null;
        notifyAll();
    }

    public synchronized long dirtyRevision() {
        return dirtyRevision;
    }

    public synchronized long savedRevision() {
        return savedRevision;
    }

    public synchronized IOException failure() {
        return failure;
    }

    public synchronized boolean isIdle() {
        return pending == null && savedRevision >= dirtyRevision;
    }

    public void awaitIdle() throws InterruptedException {
        synchronized (this) {
            while (!isIdle() && failure == null) wait();
        }
    }

    private void run() {
        while (true) {
            Work work;
            synchronized (this) {
                while (pending == null && !closing) waitUninterruptibly();
                if (pending == null && closing) return;
                if (!closing && debounceMillis > 0) waitDebounce();
                work = new Work(pending, pendingRevision);
                pending = null;
            }
            try {
                long started = System.nanoTime();
                WorldSave.save(target, work.snapshot());
                long bytes = java.nio.file.Files.size(target);
                synchronized (this) {
                    workerNanos = System.nanoTime() - started;
                    savedBytes = bytes;
                    savedRevision = Math.max(savedRevision, work.revision());
                    failure = null;
                    notifyAll();
                }
            } catch (IOException saveFailure) {
                synchronized (this) {
                    failure = saveFailure;
                    pending = work.snapshot();
                    pendingRevision = work.revision();
                    pendingAtNanos = System.nanoTime();
                    notifyAll();
                }
                if (closing) return;
            }
        }
    }

    private void waitDebounce() {
        try {
            long debounceNanos = debounceMillis * 1_000_000L;
            while (!closing) {
                long remaining = debounceNanos - (System.nanoTime() - pendingAtNanos);
                if (remaining <= 0) return;
                long millis = remaining / 1_000_000L;
                int nanos = (int) (remaining % 1_000_000L);
                wait(millis, nanos);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            closing = true;
        }
    }

    private void waitUninterruptibly() {
        try {
            wait();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            closing = true;
        }
    }

    @Override
    public void close() throws InterruptedException {
        synchronized (this) {
            closing = true;
            notifyAll();
        }
        worker.join();
    }

    private record Work(WorldSave.Snapshot snapshot, long revision) {}
}
