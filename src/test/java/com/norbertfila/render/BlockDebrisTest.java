package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.interaction.BlockBreakEvent;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import java.nio.FloatBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockDebrisTest {
    private final BlockBreakEvent event = new BlockBreakEvent(60, new BlockPos(2, 3, 4), BlockId.GRASS);

    @Test
    void startsAtTheDestroyedBlockAndSamplesOnlyItsTextures() {
        var buffer = buffer(1);
        assertEquals(24, BlockDebris.write(buffer, List.of(event), 1, 0, 0, 0));
        assertEquals(24 * BlockDebris.FLOATS_PER_PARTICLE, buffer.position());
        var definition = BlockRegistry.DEFAULT.definition(BlockId.GRASS);
        for (int i = 0; i < buffer.position(); i += BlockDebris.FLOATS_PER_PARTICLE) {
            assertTrue(buffer.get(i) > 2 && buffer.get(i) < 3);
            assertTrue(buffer.get(i + 1) > 3 && buffer.get(i + 1) < 4);
            assertTrue(buffer.get(i + 2) > 4 && buffer.get(i + 2) < 5);
            assertTrue(buffer.get(i + 3) >= 0 && buffer.get(i + 3) <= .75);
            assertTrue(buffer.get(i + 4) >= 0 && buffer.get(i + 4) <= .75);
            float layer = buffer.get(i + 5);
            assertTrue(layer == definition.sideTexture()
                    || layer == definition.topTexture()
                    || layer == definition.bottomTexture());
            assertEquals(1, buffer.get(i + 7));
        }
    }

    @Test
    void repeatedSnapshotsDoNotRestartParticlesAndExpiredOrFutureEventsProduceNothing() {
        var first = buffer(1);
        var second = buffer(1);
        int count = BlockDebris.write(first, List.of(event), 1.5, 0, 0, 0);
        assertTrue(count > 0 && count < 24);
        assertEquals(count, BlockDebris.write(second, List.of(event), 1.5, 0, 0, 0));
        assertArrayEquals(first.array(), second.array());
        assertEquals(0, BlockDebris.write(buffer(1), List.of(event), .99, 0, 0, 0));
        assertEquals(0, BlockDebris.write(buffer(1), List.of(event), 1.66, 0, 0, 0));
        assertEquals(0, BlockDebris.write(buffer(1), List.of(), 1, 0, 0, 0));
        for (int i = 7; i < first.position(); i += BlockDebris.FLOATS_PER_PARTICLE)
            assertTrue(first.get(i) > 0 && first.get(i) <= 1);
    }

    @Test
    void cameraRelativePositionsKeepPrecisionAtLargeNegativeCoordinates() {
        var near = buffer(1);
        var far = buffer(1);
        BlockDebris.write(near, List.of(event), 1.3, 2.5, 3.5, 4.5);
        var shifted = new BlockBreakEvent(60, new BlockPos(-1_500_000_000, 3, 4), BlockId.GRASS);
        BlockDebris.write(far, List.of(shifted), 1, -1_499_999_999.5, 3.5, 4.5);
        for (int i = 0; i < far.position(); i += BlockDebris.FLOATS_PER_PARTICLE) {
            assertTrue(Math.abs(far.get(i)) <= .5);
            assertTrue(Math.abs(far.get(i + 1)) <= .5);
            assertTrue(Math.abs(far.get(i + 2)) <= .5);
        }
        var shiftedCamera = buffer(1);
        BlockDebris.write(shiftedCamera, List.of(event), 1.3, 3.5, 3.5, 4.5);
        for (int i = 0; i < near.position(); i += BlockDebris.FLOATS_PER_PARTICLE) {
            assertEquals(near.get(i) - 1, shiftedCamera.get(i), 1e-6);
            assertEquals(near.get(i + 1), shiftedCamera.get(i + 1));
        }
    }

    private static FloatBuffer buffer(int events) {
        return FloatBuffer.allocate(events * BlockDebris.PARTICLES_PER_BLOCK * BlockDebris.FLOATS_PER_PARTICLE);
    }
}
