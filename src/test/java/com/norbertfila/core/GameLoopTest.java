package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.player.PlayerState;
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
        player.setPosition(new PlayerState.Position(8.5, 9, 9.5));

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
        player.setPosition(new PlayerState.Position(8.5, 9, 7.5));
        Hotbar hotbar = new Hotbar();
        Camera camera = editingCamera();
        BlockInteraction interaction = new BlockInteraction(world, 64);

        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, -1)
                .isEmpty());
        assertEquals(0, world.changes().size());
        player.setMode(world, PlayerController.Mode.SPECTATOR);
        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, -1)
                .isEmpty());
        assertEquals(0, world.changes().size());
        player.setPosition(new PlayerState.Position(8.5, 9, 9.5));
        assertTrue(player.setMode(world, PlayerController.Mode.CREATIVE));
        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, -1)
                .orElseThrow()
                .changed());
    }

    @Test
    void selectsAndPlacesGlassThenSimultaneousClicksRemoveWithoutReplacing() {
        World world = editableWorld();
        Hotbar hotbar = new Hotbar();
        BlockInteraction interaction = new BlockInteraction(world, 64);
        Camera camera = editingCamera();
        PlayerController player = new PlayerController();

        var placement = GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, 12)
                .orElseThrow();
        assertTrue(placement.changed());
        assertEquals(BlockId.GLASS, world.getBlock(new BlockPos(8, 9, 7)).orElseThrow());
        assertEquals(12, hotbar.selectedSlot());

        var removal = GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, true, true, -1)
                .orElseThrow();

        assertTrue(removal.changed());
        assertEquals(BlockId.AIR, world.getBlock(new BlockPos(8, 9, 7)).orElseThrow());
        assertEquals(BlockId.STONE, world.getBlock(new BlockPos(8, 9, 6)).orElseThrow());
        assertEquals(1, world.changes().size());
    }

    @Test
    void scrollSelectsHotbarItemButBlockedInputDoesNotChangeSelection() {
        World world = editableWorld();
        Hotbar hotbar = new Hotbar();
        BlockInteraction interaction = new BlockInteraction(world, 64);
        Camera camera = editingCamera();
        PlayerController player = new PlayerController();

        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, false, -1, 1)
                .isEmpty());
        assertEquals(15, hotbar.selectedSlot());
        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, true, false, false, -1, -1)
                .isEmpty());
        assertEquals(15, hotbar.selectedSlot());
    }

    @Test
    void spectatorClicksAndHotbarSelectionHaveNoEditingSideEffects() {
        World world = editableWorld();
        Hotbar hotbar = new Hotbar();
        PlayerController player = new PlayerController();
        player.setMode(world, PlayerController.Mode.SPECTATOR);
        assertTrue(GameLoop.updateBlockEditing(
                        editingCamera(), player, new BlockInteraction(world, 64), hotbar, false, true, true, 2)
                .isEmpty());
        assertEquals(0, hotbar.selectedSlot());
        assertEquals(1, world.getChunk(new ChunkPos(0, 0, 0)).revision());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().drainDirtyTiles().isEmpty());
    }

    @Test
    void placementAboveCrouchedHeadUsesCurrentBodyAndPreventsStandingIntoThePlacedBlock() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(8, 10, 6, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        PlayerController player = new PlayerController();
        player.setPosition(new PlayerState.Position(8.5, 8.4, 7.5));
        Camera camera = new Camera();
        camera.setPosition(8.5f, 10.5f, 9.5f);
        camera.updateLook(0, -25 / .11f);
        Hotbar hotbar = new Hotbar();
        BlockInteraction interaction = new BlockInteraction(world, 64);
        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, -1)
                .isEmpty());
        player.tick(world, new PlayerInput(false, false, false, false, false, true, false), 1.0 / 60, 0, 0);
        assertTrue(player.state().crouching());
        assertTrue(GameLoop.updateBlockEditing(camera, player, interaction, hotbar, false, false, true, -1)
                .orElseThrow()
                .changed());
        assertEquals(BlockId.GRASS, world.getBlock(new BlockPos(8, 10, 7)).orElseThrow());
        player.tick(world, PlayerInput.NONE, 1.0 / 60, 0, 0);
        assertTrue(player.state().crouching());
        assertTrue(player.state().body().maxY() <= 10);
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
