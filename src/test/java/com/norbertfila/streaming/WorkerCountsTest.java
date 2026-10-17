package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkerCountsTest {
    @Test
    void rejectsInvalidCountsBeforeStartingThreads() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerCounts(0, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new WorkerCounts(2, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new WorkerCounts(2, 2, 65));
        assertEquals(new WorkerCounts(2, 2, 2), WorkerCounts.DEFAULT);
    }
}
