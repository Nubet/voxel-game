package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.Chunk;
import org.junit.jupiter.api.Test;

class BlockNeighborhoodTest {
    @Test
    void distinguishesMissingAndPresentNeighbors() {
        Chunk centerChunk = new Chunk();
        Chunk positiveXChunk = new Chunk();
        BlockNeighborhood neighborhood =
                new BlockNeighborhood(centerChunk.snapshot(), positiveXChunk.snapshot(), null, null, null, null, null);

        assertTrue(neighborhood.neighbor(BlockNeighborhood.Side.POSITIVE_X).isPresent());
        assertFalse(neighborhood.neighbor(BlockNeighborhood.Side.NEGATIVE_X).isPresent());
        assertEquals(
                positiveXChunk.snapshot().revision(),
                neighborhood
                        .neighbor(BlockNeighborhood.Side.POSITIVE_X)
                        .orElseThrow()
                        .revision());
    }

    @Test
    void centerOnlyNeighborhoodHasStableVersionForSameSnapshots() {
        Chunk centerChunk = new Chunk();
        var first = BlockNeighborhood.centerOnly(centerChunk.snapshot());
        var second = BlockNeighborhood.centerOnly(centerChunk.snapshot());

        assertEquals(first.revision(), second.revision());
    }
}
