package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ColumnCacheTest {
    @Test
    void concurrentRequestsForOneColumnComputeItOnce() throws Exception {
        var cache = new ColumnCache<Object>(4);
        AtomicInteger computations = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(6)) {
            for (int request = 0; request < 6; request++)
                results.add(workers.submit(() -> cache.get(-3, 7, () -> {
                    computations.incrementAndGet();
                    try {
                        release.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return new Object();
                })));
            release.countDown();
            Object first = results.get(0).get();
            for (Future<Object> result : results) assertSame(first, result.get());
        }
        assertEquals(1, computations.get());
        assertEquals(new ColumnCache.Stats(1, 5, 1), cache.stats());
    }

    @Test
    void evictsLeastRecentlyUsedColumnBeyondCapacity() {
        var cache = new ColumnCache<String>(2);
        cache.get(0, 0, () -> "a");
        cache.get(1, 0, () -> "b");
        cache.get(0, 0, () -> "unused");
        cache.get(2, 0, () -> "c");
        assertEquals("a", cache.get(0, 0, () -> "recomputed"));
        assertEquals("b2", cache.get(1, 0, () -> "b2"));
        assertEquals(2, cache.stats().size());
    }

    @Test
    void distinguishesNegativeCoordinates() {
        var cache = new ColumnCache<String>(8);
        cache.get(-1, 0, () -> "west");
        cache.get(0, -1, () -> "north");
        assertEquals("west", cache.get(-1, 0, () -> "x"));
        assertEquals("north", cache.get(0, -1, () -> "x"));
    }

    @Test
    void failedComputationIsRetried() {
        var cache = new ColumnCache<String>(2);
        assertThrows(
                IllegalStateException.class,
                () -> cache.get(5, 5, () -> {
                    throw new IllegalStateException("noise out of range");
                }));
        assertEquals("ok", cache.get(5, 5, () -> "ok"));
    }
}
