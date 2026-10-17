package com.norbertfila.generation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

final class SmallWaterBodyClassifier {
    // shortcut: enclosed sea-level pools up to 1024 columns become lakes; larger lakes need regional connectivity.
    private static final int MAX_POOL_AREA = 1024;
    private static final int CACHE_CAPACITY = 16384;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private final BiPredicate<Integer, Integer> flooded;
    private final Map<Long, WaterKind> cache = new LinkedHashMap<>(CACHE_CAPACITY, .75f, true);

    SmallWaterBodyClassifier(BiPredicate<Integer, Integer> flooded) {
        this.flooded = flooded;
    }

    WaterKind classify(int x, int z) {
        long origin = key(x, z);
        var known = cached(origin);
        if (known != null) return known;
        var pending = new ArrayDeque<Long>();
        var visited = new HashSet<Long>();
        var water = new HashSet<Long>();
        pending.add(origin);
        visited.add(origin);
        water.add(origin);
        WaterKind result = WaterKind.LAKE;
        search:
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            long current = pending.removeFirst();
            int cx = (int) (current >> 32), cz = (int) current;
            for (int[] direction : DIRECTIONS) {
                long nx = (long) cx + direction[0], nz = (long) cz + direction[1];
                if (nx < Integer.MIN_VALUE
                        || nx > Integer.MAX_VALUE
                        || nz < Integer.MIN_VALUE
                        || nz > Integer.MAX_VALUE) {
                    result = WaterKind.OCEAN;
                    break search;
                }
                long next = key((int) nx, (int) nz);
                if (!visited.add(next)) continue;
                known = cached(next);
                if (known != null) {
                    result = known;
                    break search;
                }
                if (!flooded.test((int) nx, (int) nz)) continue;
                water.add(next);
                if (water.size() > MAX_POOL_AREA) {
                    result = WaterKind.OCEAN;
                    break search;
                }
                pending.addLast(next);
            }
        }
        synchronized (cache) {
            for (long position : water) cache.put(position, result);
            while (cache.size() > CACHE_CAPACITY) {
                var oldest = cache.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
        return result;
    }

    private WaterKind cached(long position) {
        synchronized (cache) {
            return cache.get(position);
        }
    }

    private static long key(int x, int z) {
        return (long) x << 32 | Integer.toUnsignedLong(z);
    }
}
