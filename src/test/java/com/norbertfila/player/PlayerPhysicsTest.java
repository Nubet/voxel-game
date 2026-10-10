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

        PlayerState step = physics.step(world, PlayerState.at(new PlayerState.Position(.5, 2, .5)), 0, 0, false, .5);

        assertEquals(1, step.position().y(), 0.000001);
        assertTrue(step.grounded());
    }

    @Test
    void stopsAtWall() {
        World world = worldWithBlocks(new BlockPos(2, 1, 0));
        PlayerPhysics physics = new PlayerPhysics();

        PlayerState step =
                physics.step(world, PlayerState.at(new PlayerState.Position(1.5, 1, .5)), 2, 0, false, 1.0 / 60);

        assertEquals(1.7, step.position().x(), 0.000001);
    }

    @Test
    void stopsJumpingAtCeiling() {
        World world = worldWithBlocks(new BlockPos(0, 0, 0), new BlockPos(0, 3, 0));
        PlayerPhysics physics = new PlayerPhysics();
        PlayerState grounded =
                physics.step(world, PlayerState.at(new PlayerState.Position(.5, 1, .5)), 0, 0, false, 1.0 / 60);

        PlayerState step = physics.step(world, grounded, 0, 0, true, .1);

        assertEquals(1.2, step.position().y(), 0.000001);
        assertEquals(0, step.velocityY(), 0.000001);
    }

    @Test
    void resolvesDiagonalMovementAgainstWall() {
        World world = worldWithBlocks(new BlockPos(2, 1, 0));
        PlayerPhysics physics = new PlayerPhysics();

        PlayerState step =
                physics.step(world, PlayerState.at(new PlayerState.Position(1.5, 1, .5)), 2, 2, false, 1.0 / 60);

        assertEquals(1.7, step.position().x(), 0.000001);
        assertEquals(2.5, step.position().z(), 0.000001);
    }

    @Test
    void collidesWithBlockAcrossChunkBoundary() {
        World world = worldWithBlocks(new BlockPos(16, 1, 0));
        PlayerPhysics physics = new PlayerPhysics();

        PlayerState step =
                physics.step(world, PlayerState.at(new PlayerState.Position(15.5, 1, .5)), 2, 0, false, 1.0 / 60);

        assertEquals(15.7, step.position().x(), 0.000001);
    }

    @Test
    void physicsHasNoHiddenMotionSharedBetweenPlayers() {
        World world = worldWithBlocks(new BlockPos(0, 0, 0));
        PlayerPhysics physics = new PlayerPhysics();
        PlayerState start = PlayerState.at(new PlayerState.Position(.5, 4, .5));
        PlayerState first = physics.step(world, start, 0, 0, false, 1.0 / 60);
        physics.step(world, PlayerState.at(new PlayerState.Position(.5, 20, .5)), 0, 0, false, .2);

        assertEquals(first, physics.step(world, start, 0, 0, false, 1.0 / 60));
        assertEquals(0, start.velocityY());
        assertEquals(-25.0 / 60, first.velocityY(), 1e-9);
        assertEquals(
                -50.0 / 60, physics.step(world, first, 0, 0, false, 1.0 / 60).velocityY(), 1e-9);
    }

    private static World worldWithBlocks(BlockPos... positions) {
        World world = new World();
        for (int y = -2; y <= 1; y++)
            for (int z = -1; z <= 1; z++)
                for (int x = -1; x <= 1; x++) world.putChunk(new ChunkPos(x, y, z), new Chunk());
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
