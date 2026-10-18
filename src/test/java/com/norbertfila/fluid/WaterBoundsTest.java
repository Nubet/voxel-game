package com.norbertfila.fluid;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.HashSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WaterBoundsTest {
    @ParameterizedTest
    @ValueSource(ints = {-65, 256})
    void outsideWorldIsBlockedRatherThanAnUnloadedDependency(int y) {
        var missing = new HashSet<ChunkPos>();
        assertEquals(FluidFlowRules.BLOCKED, FluidFlowRules.nextState(new World(), new BlockPos(0, y, 0), missing));
        assertTrue(missing.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {-64, 255})
    void boundarySourcesNeverScheduleUpdatesOutsideWorld(int y) {
        var world = new World();
        var position = new BlockPos(8, y, 8);
        world.putChunk(Coordinates.worldToChunk(position), new Chunk());
        world.setBlock(position, BlockId.WATER, 8);
        var simulator = new FluidSimulator(world, 8);
        simulator.onBlockChanged(position, 0);
        simulator.tick(15);
        assertTrue(simulator.snapshot(15).updates().stream()
                .allMatch(update -> WorldBounds.containsY(update.position().y())));
        assertTrue(world.changes().changes().stream()
                .allMatch(change -> WorldBounds.containsY(change.position().y())));
    }
}
