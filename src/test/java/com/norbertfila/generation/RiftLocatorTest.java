package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class RiftLocatorTest {
    @Test
    void findsNearestAcceptedCenterWithoutGeneratingChunks() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        var result = new RiftLocator(terrain).locate(.5, .5, 8192, System.nanoTime() + 10_000_000_000L);
        assertEquals(RiftLocator.Status.FOUND, result.status());
        var match = result.match().orElseThrow();
        var plan = terrain.riftAt(match.x(), match.z());
        assertNotNull(plan);
        assertEquals(WaterKind.NONE, terrain.sample(match.x(), match.z()).waterKind());
        assertEquals(terrain.surfaceHeight(match.x(), match.z()), match.surfaceY());
        assertEquals(0, terrain.columnCacheStats().misses());
        var planner = new RiftPlanner(42);
        for (int rz = -8; rz <= 8; rz++)
            for (int rx = -8; rx <= 8; rx++) {
                var candidate = planner.plan(rx, rz);
                if (candidate.isEmpty()) continue;
                var center = candidate.get().nodes().get(3);
                int x = (int) Math.floor(center.x()), z = (int) Math.floor(center.z());
                if (Math.hypot(x, z) >= match.distance()) continue;
                assertNull(terrain.riftAt(x, z), "Closer accepted center: " + x + ", " + z);
            }
        var atCenter = new RiftLocator(terrain).locate(match.x() + .5, match.z() + .5, 0, Long.MAX_VALUE);
        assertEquals(RiftLocator.Status.FOUND, atCenter.status());
        assertEquals(0, atCenter.match().orElseThrow().distance());
    }

    @Test
    void respectsRadiusDeadlineCancellationAndWorldBounds() {
        var locator = new RiftLocator(new TerrainGenerator(42, TerrainConfig.defaults()));
        assertEquals(
                RiftLocator.Status.NOT_FOUND,
                locator.locate(.5, .5, 0, Long.MAX_VALUE).status());
        var limited = locator.locate(0, 0, 32768, 0);
        assertEquals(RiftLocator.Status.LIMIT_REACHED, limited.status());
        assertTrue(limited.match().isEmpty());
        assertDoesNotThrow(() -> locator.locate(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> locator.locate(Double.NaN, 0, 10, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> locator.locate(0, 0, 32769, Long.MAX_VALUE));
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> locator.locate(0, 0, 100, Long.MAX_VALUE));
        } finally {
            Thread.interrupted();
        }
    }
}
