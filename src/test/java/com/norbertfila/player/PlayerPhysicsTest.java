package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class PlayerPhysicsTest {
    @Test
    void landsOnFloorWithoutFallingThroughIt() {
        World world = worldWithBlocks(new BlockPos(0, 0, 0));
        PlayerPhysics physics = new PlayerPhysics();

        PlayerPhysics.Step step = physics.step(world, new PlayerController.Position(.5, 2, .5), 0, 0, false, .5);

        assertEquals(1, step.position().y(), 0.000001);
        assertTrue(step.grounded());
    }

    @Test
    void stopsAtWall() {
        World world = worldWithBlocks(new BlockPos(2, 1, 0));
        PlayerPhysics physics = new PlayerPhysics();

        PlayerPhysics.Step step = physics.step(world, new PlayerController.Position(1.5, 1, .5), 2, 0, false, 1);

        assertEquals(1.7, step.position().x(), 0.000001);
    }

    @Test
    void stopsJumpingAtCeiling() {
        World world = worldWithBlocks(new BlockPos(0, 0, 0), new BlockPos(0, 3, 0));
        PlayerPhysics physics = new PlayerPhysics();
        physics.step(world, new PlayerController.Position(.5, 1, .5), 0, 0, false, 1.0 / 60);

        PlayerPhysics.Step step = physics.step(world, new PlayerController.Position(.5, 1, .5), 0, 0, true, .1);

        assertEquals(1.2, step.position().y(), 0.000001);
        assertEquals(0, step.verticalVelocity(), 0.000001);
    }

    @Test
    void resolvesDiagonalMovementAgainstWall() {
        World world = worldWithBlocks(new BlockPos(2, 1, 0));
        PlayerPhysics physics = new PlayerPhysics();

        PlayerPhysics.Step step = physics.step(world, new PlayerController.Position(1.5, 1, .5), 2, 2, false, 1);

        assertEquals(1.7, step.position().x(), 0.000001);
        assertEquals(2.5, step.position().z(), 0.000001);
    }

    @Test
    void collidesWithBlockAcrossChunkBoundary() {
        World world = worldWithBlocks(new BlockPos(16, 1, 0));
        PlayerPhysics physics = new PlayerPhysics();

        PlayerPhysics.Step step = physics.step(world, new PlayerController.Position(15.5, 1, .5), 2, 0, false, 1);

        assertEquals(15.7, step.position().x(), 0.000001);
    }

    private static World worldWithBlocks(BlockPos... positions) {
        World world = new World();
        for (BlockPos position : positions) {
            ChunkPos chunkPosition = new ChunkPos(
                    Math.floorDiv(position.x(), Chunk.SIZE),
                    Math.floorDiv(position.y(), Chunk.SIZE),
                    Math.floorDiv(position.z(), Chunk.SIZE));
            Chunk chunk = world.getChunk(chunkPosition);
            if (chunk == null) {
                chunk = new Chunk();
                world.putChunk(chunkPosition, chunk);
            }
            chunk.set(
                    Math.floorMod(position.x(), Chunk.SIZE),
                    Math.floorMod(position.y(), Chunk.SIZE),
                    Math.floorMod(position.z(), Chunk.SIZE),
                    BlockId.STONE);
        }
        return world;
    }
}
