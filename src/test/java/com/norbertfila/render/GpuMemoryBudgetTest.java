package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GpuMemoryBudgetTest {
    @Test
    void tracksReservationsAndDeferredReleases() {
        GpuMemoryBudget budget = new GpuMemoryBudget(100);

        assertTrue(budget.tryReserve(60));
        assertFalse(budget.tryReserve(41));
        budget.release(60);

        assertTrue(budget.availableBytes() == 100);
        assertTrue(budget.uploadedBytes() == 60);
        assertTrue(budget.deletedBytes() == 60);
        assertTrue(budget.peakBytes() == 60);
    }
}
