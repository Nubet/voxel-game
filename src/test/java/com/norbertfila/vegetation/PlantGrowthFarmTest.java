package com.norbertfila.vegetation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class PlantGrowthFarmTest {
    @Test
    void largeFarmRetainsBoundedWorkAndDoesNotAccumulateStaleSchedules() {
        var world = new World();
        var growth = new PlantGrowthSystem(world, 64, 42);
        world.setBlockChangeListener(growth::onBlockChanged);
        var chunks = new LinkedHashMap<ChunkPos, Chunk>();
        for (int i = 0; i < 32; i++) {
            var position = new ChunkPos(i * 3, 0, 0);
            var chunk = new Chunk();
            for (int z = 1; z <= 14; z++)
                for (int x = 1; x <= 13; x += 2) {
                    chunk.set(x, 0, z, BlockId.SAND);
                    chunk.set(x + 1, 0, z, BlockId.WATER);
                    chunk.set(x, 1, z, BlockId.REED);
                }
            chunks.put(position, chunk);
            world.putChunk(position, chunk);
            growth.onChunkChanged(position);
        }
        for (int i = 0; i < 32; i++) growth.update(0, false);
        int plants = 32 * 7 * 14;
        assertEquals(plants, growth.snapshot().plants().size());
        assertEquals(plants, growth.activeUpdates());
        for (int cycle = 0; cycle < 20; cycle++) {
            for (var position : chunks.keySet()) {
                world.removeChunk(position);
                growth.onChunkChanged(position);
            }
            assertEquals(0, growth.activeUpdates());
            for (int i = 0; i < 32; i++) assertEquals(0, growth.update(0, true).processed());
            for (var entry : chunks.entrySet()) {
                world.putChunk(entry.getKey(), entry.getValue());
                growth.onChunkChanged(entry.getKey());
            }
            for (int i = 0; i < 32; i++) growth.update(0, false);
            assertEquals(plants, growth.activeUpdates());
            assertEquals(plants, growth.snapshot().plants().size());
        }
        int changed = 0;
        for (int i = 0; i < 100; i++) {
            var result = growth.update(1_000_000, true);
            assertTrue(result.processed() <= PlantGrowthSystem.DEFAULT_BUDGET);
            changed += result.changedBlocks();
        }
        assertEquals(plants * 2, changed);
        assertEquals(0, growth.activeUpdates());
        assertEquals(chunks.size(), world.residentCount());
        for (var state : growth.snapshot().plants()) {
            assertEquals(2, state.cycle());
            assertEquals(
                    BlockId.REED,
                    world.getBlock(new BlockPos(
                                    state.root().x(), 3, state.root().z()))
                            .orElseThrow());
            assertEquals(
                    BlockId.AIR,
                    world.getBlock(new BlockPos(
                                    state.root().x(), 4, state.root().z()))
                            .orElseThrow());
        }
    }
}
