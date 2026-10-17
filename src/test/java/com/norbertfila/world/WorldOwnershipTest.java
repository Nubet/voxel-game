package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class WorldOwnershipTest {
    @Test
    void workersCanReadSnapshotsButCannotAccessLiveWorld() throws Exception {
        var world = new World();
        var position = new ChunkPos(0, 0, 0);
        world.putChunk(position, Chunk.uniform(BlockId.STONE));
        var snapshot = world.blockNeighborhood(position);
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                        assertThrows(IllegalStateException.class, () -> world.getChunk(position));
                        assertThrows(IllegalStateException.class, () -> world.removeChunk(position));
                        assertThrows(IllegalStateException.class, () -> world.blockNeighborhood(position));
                        assertNotNull(snapshot.center());
                    })
                    .get();
        }
        assertTrue(world.hasChunk(position));
    }
}
