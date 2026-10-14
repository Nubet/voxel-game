package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class ViewDemandTest {
    @Test
    void squareRadiusCountsAndUpdatesOnlyWhenChunkChanges() {
        ViewDemand demand = new ViewDemand(0, -1, 1, ViewDemand.Shape.SQUARE);

        assertTrue(demand.update(new ChunkPos(-1, 0, -2)));
        assertEquals(3, demand.requested().size());
        assertEquals(new ChunkPos(-1, 0, -2), demand.requested().get(0));
        assertFalse(demand.update(new ChunkPos(-1, 0, -2)));

        demand.configure(1, -1, 1, ViewDemand.Shape.SQUARE);
        assertTrue(demand.update(new ChunkPos(-1, 0, -2)));
        assertEquals(27, demand.requested().size());
        assertEquals(new ChunkPos(-1, 0, -2), demand.requested().get(0));
    }

    @Test
    void requestsNearestSectionsFirst() {
        ViewDemand demand = new ViewDemand(3, -2, 4, ViewDemand.Shape.SQUARE);
        ChunkPos center = new ChunkPos(-5, 1, 7);
        demand.update(center);

        long previous = -1;
        for (ChunkPos position : demand.requested()) {
            long distance = ChunkPriority.distanceSquared(position, center);
            assertTrue(distance >= previous);
            previous = distance;
        }
        assertEquals(center, demand.requested().get(0));
    }

    @Test
    void circularRadiusExcludesCorners() {
        ViewDemand demand = new ViewDemand(2, 0, 0, ViewDemand.Shape.CIRCLE);

        demand.update(new ChunkPos(0, 0, 0));

        assertEquals(13, demand.requested().size());
        assertFalse(demand.requested().contains(new ChunkPos(2, 0, 2)));
    }

    @Test
    void settingsChangeInvalidatesExistingDemand() {
        ViewDemand demand = new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE);
        demand.update(new ChunkPos(0, 0, 0));

        assertTrue(demand.configure(2, -1, 1, ViewDemand.Shape.SQUARE));
        assertTrue(demand.update(new ChunkPos(0, 0, 0)));
        assertEquals(75, demand.requested().size());
    }
}
