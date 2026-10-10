package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.player.PlayerController;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class GameLoopTest {
    @Test
    void clampsDeltaToSafeRange() {
        assertEquals(0.0, GameLoop.clampDelta(-1.0));
        assertEquals(0.016, GameLoop.clampDelta(0.016));
        assertEquals(0.1, GameLoop.clampDelta(2.0));
    }

    @Test
    void selectsBeforePlacementAndPassesEditMetadataBackToTheLoop() {
        World world = editableWorld();
        Camera camera = editingCamera();
        Hotbar hotbar = new Hotbar();
        PlayerController player = new PlayerController();
        player.setMode(PlayerController.Mode.SURVIVAL);
        player.setPosition(new PlayerController.Position(8.5, 9, 9.5));

        var result = GameLoop.updateBlockEditing(
                        camera, player, new BlockInteraction(world, 64), hotbar, false, false, true, 1)
                .orElseThrow();

        assertTrue(result.changed());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(8, 9, 7)).orElseThrow());
        assertEquals(1, hotbar.selectedSlot());
        assertEquals(1, result.dirtyChunks().size());
        assertEquals(2, result.chunkRevision());
    }

    @Test
    void panelAndToggleFrameBlockBothEditsAndSelection() {
        World world = editableWorld();
        Hotbar hotbar = new Hotbar();
        var result = GameLoop.updateBlockEditing(
                editingCamera(), new PlayerController(), new BlockInteraction(world, 64), hotbar, true, true, true, 2);

        assertTrue(result.isEmpty());
        assertEquals(0, hotbar.selectedSlot());
        assertEquals(1, world.getChunk(new ChunkPos(0, 0, 0)).revision());
        assertEquals(0, world.changes().size());
    }

    @Test
    void placementUsesTheCurrentPlayerBodyRatherThanTheRenderedCameraPosition() {
        World world = editableWorld();
        PlayerController player = new PlayerController();
        player.setMode(PlayerController.Mode.SURVIVAL);
        player.setPosition(new PlayerController.Position(8.5, 9, 7.5));
        Hotbar hotbar = new Hotbar();
        Camera camera = editingCamera();
        BlockInteraction interaction = new BlockInteraction(world, 64);

        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, -1)
                .isEmpty());
        assertEquals(0, world.changes().size());
        player.setMode(PlayerController.Mode.SPECTATOR);
        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, -1)
                .orElseThrow()
                .changed());
    }

    @Test
    void emptySlotDoesNotPlaceButStillAllowsRemovalAndSimultaneousClicksRemoveOnly() {
        World world = editableWorld();
        Hotbar hotbar = new Hotbar();
        BlockInteraction interaction = new BlockInteraction(world, 64);
        Camera camera = editingCamera();
        PlayerController player = new PlayerController();

        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, 8)
                .isEmpty());
        assertEquals(0, world.changes().size());
        var removal = GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, true, true, -1)
                .orElseThrow();

        assertTrue(removal.changed());
        assertEquals(BlockId.AIR, world.getBlock(new BlockPos(8, 9, 6)).orElseThrow());
        assertEquals(1, world.changes().size());
    }

    private static World editableWorld() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(8, 9, 6, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        return world;
    }

    private static Camera editingCamera() {
        Camera camera = new Camera();
        camera.setPosition(8.5f, 10.62f, 9.5f);
        return camera;
    }
}
