package com.norbertfila.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Bounded least-recently-used cache of immutable values keyed by two-dimensional generation coordinates. Concurrent requests for the
 * same key share one computation: the first caller computes while the others wait for its result. A failed
 * computation is not cached.
 */
final class GenerationCache<T> {
    private final int capacity;
    private final Map<Long, FutureTask<T>> entries;
    private final LongAdder hits = new LongAdder(), misses = new LongAdder();

    GenerationCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        entries = new LinkedHashMap<>(capacity * 2, .75f, true);
    }

    T get(int chunkX, int chunkZ, Supplier<T> compute) {
        Objects.requireNonNull(compute);
        long key = (long) chunkX << 32 | Integer.toUnsignedLong(chunkZ);
        FutureTask<T> task;
        boolean owner = false;
        synchronized (entries) {
            task = entries.get(key);
            if (task == null) {
                task = new FutureTask<>(compute::get);
                entries.put(key, task);
                owner = true;
                if (entries.size() > capacity) {
                    var eldest = entries.entrySet().iterator();
                    eldest.next();
                    eldest.remove();
                }
            }
        }
        (owner ? misses : hits).increment();
        if (owner) task.run();
        try {
            return task.get();
        } catch (ExecutionException failed) {
            synchronized (entries) {
                entries.remove(key, task);
            }
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Generation computation failed", failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for generation", interrupted);
        }
    }

    Stats stats() {
        synchronized (entries) {
            return new Stats(entries.size(), hits.sum(), misses.sum());
        }
    }

    record Stats(int size, long hits, long misses) {}
}
