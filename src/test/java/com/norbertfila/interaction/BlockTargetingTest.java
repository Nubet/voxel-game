package com.norbertfila.interaction;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BlockTargetingTest {
    @ParameterizedTest
    @CsvSource({
        "SURVIVAL,1.5,true", "SURVIVAL,1.499999,false", "SURVIVAL,0,false",
        "CREATIVE,0,true", "CREATIVE,-0.000001,false", "SPECTATOR,1.5,false"
    })
    void usesTheSelectedModesReachIncludingItsExactBoundary(Mode mode, double originX, boolean reachable) {
        World world = world();
        world.getChunk(new ChunkPos(0, 0, 0)).set(6, 1, 1, BlockId.STONE);
        var hit = new BlockTargeting(world).find(mode, originX, 1.5, 1.5, 1, 0, 0);
        assertEquals(reachable, hit.isPresent());
        if (reachable) {
            assertEquals(new BlockPos(6, 1, 1), hit.orElseThrow().block());
            assertEquals(6 - originX, hit.orElseThrow().distance());
            assertEquals(new VoxelRaycaster.Normal(-1, 0, 0), hit.orElseThrow().normal());
        }
        assertEquals(0, world.changes().size());
    }

    @Test
    void modeChangesImmediatelyUpdateWaterAndPlantTargetingWithoutEditingTheWorld() {
        World world = world();
        Chunk chunk = world.getChunk(new ChunkPos(0, 0, 0));
        chunk.set(1, 1, 1, BlockId.WATER);
        chunk.set(2, 1, 1, BlockId.POPPY);
        chunk.set(3, 1, 1, BlockId.STONE);
        long revision = chunk.revision();
        var targeting = new BlockTargeting(world);
        assertEquals(
                BlockId.POPPY,
                targeting
                        .find(Mode.SURVIVAL, .5, 1.5, 1.5, 1, 0, 0)
                        .orElseThrow()
                        .blockId());
        assertEquals(
                BlockId.WATER,
                targeting
                        .find(Mode.CREATIVE, .5, 1.5, 1.5, 1, 0, 0)
                        .orElseThrow()
                        .blockId());
        assertTrue(targeting.find(Mode.SPECTATOR, .5, 1.5, 1.5, 1, 0, 0).isEmpty());
        assertEquals(
                BlockId.POPPY,
                targeting
                        .find(Mode.SURVIVAL, .5, 1.5, 1.5, 1, 0, 0)
                        .orElseThrow()
                        .blockId());
        assertEquals(revision, chunk.revision());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().drainDirtyTiles().isEmpty());
    }

    @Test
    void unavailableTerrainStopsTargetingWithoutRequestingChunks() {
        World world = world();
        world.putChunk(new ChunkPos(2, 0, 0), new Chunk());
        world.getChunk(new ChunkPos(2, 0, 0)).set(0, 1, 1, BlockId.STONE);
        world.setBlock(new BlockPos(16, 1, 1), BlockId.STONE, 8);
        var targeting = new BlockTargeting(world);
        assertTrue(targeting.find(Mode.CREATIVE, 15.5, 1.5, 1.5, 1, 0, 0).isEmpty());
        assertTrue(targeting.find(Mode.SURVIVAL, 16.5, 1.5, 1.5, 1, 0, 0).isEmpty());
        assertFalse(world.isChunkRequested(new ChunkPos(1, 0, 0)));
        assertEquals(3, world.residentCount());
    }

    private static World world() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        world.putChunk(new ChunkPos(-1, 0, 0), new Chunk());
        return world;
    }
}
