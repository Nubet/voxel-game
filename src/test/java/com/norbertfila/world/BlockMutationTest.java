package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class BlockMutationTest {
    @Test
    void observesRealChangesAndCascadesInOrderAfterTheWorldIsUpdated() {
        var world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        var edits = new WorldEdit(world, 64);
        var ground = new BlockPos(2, 2, 2);
        var root = new BlockPos(2, 3, 2);
        var top = new BlockPos(2, 4, 2);
        edits.setBlock(ground, BlockId.SAND);
        edits.setBlock(new BlockPos(3, 2, 2), BlockId.WATER);
        edits.setBlock(root, BlockId.REED);
        edits.setBlock(top, BlockId.REED);
        var changes = new ArrayList<BlockMutation>();
        world.setBlockChangeListener(change -> {
            assertEquals(
                    Byte.toUnsignedInt(change.block()),
                    world.getBlock(change.position()).orElseThrow());
            changes.add(change);
        });
        edits.setBlock(root, BlockId.REED);
        assertTrue(changes.isEmpty());
        edits.setBlock(ground, BlockId.AIR);
        assertEquals(
                java.util.List.of(ground, root, top),
                changes.stream().map(BlockMutation::position).toList());
        assertEquals(BlockId.SAND, changes.getFirst().previous().orElseThrow());
        edits.setBlock(ground, BlockId.SAND);
        edits.setBlock(root, BlockId.REED);
        assertEquals(BlockId.AIR, changes.getLast().previous().orElseThrow());
    }

    @Test
    void reportsFluidLevelChangesAndPreservesPreviousState() {
        var world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        var position = new BlockPos(2, 2, 2);
        world.setBlock(position, BlockId.WATER, 64);
        var changes = new ArrayList<BlockMutation>();
        world.setBlockChangeListener(changes::add);
        world.setBlock(position, BlockId.WATER, WaterState.flowing(3), 64);
        assertEquals(1, changes.size());
        assertEquals(WaterState.SOURCE, changes.getFirst().previousWaterState().orElseThrow());
        assertEquals(WaterState.flowing(3), changes.getFirst().waterState());
    }
}
