package com.norbertfila.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BlockInteractionTest {
    private static final Aabb DISTANT_BODY =
            PlayerState.at(new PlayerState.Position(0, 10, 0)).body();

    @Test
    void replacesPlantsInPlaceButCannotUseThemAsFullBlockAnchors() {
        for (byte id : new byte[] {
            BlockId.SHORT_GRASS, BlockId.FERN, BlockId.DANDELION_YELLOW, BlockId.DANDELION_WHITE, BlockId.POPPY
        }) {
            World world = worldWithBlock(new BlockPos(8, 0, 8));
            world.setBlock(new BlockPos(8, 0, 8), BlockId.GRASS, 8);
            world.setBlock(new BlockPos(8, 1, 8), id, 8);
            var interaction = new BlockInteraction(world, 8);
            assertTrue(interaction
                    .place(8.5, 1.5, 5, 0, 0, 1, BlockId.STONE, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                    .orElseThrow()
                    .changed());
            assertEquals(BlockId.STONE, world.getBlock(new BlockPos(8, 1, 8)).orElseThrow());
            assertEquals(BlockId.AIR, world.getBlock(new BlockPos(8, 1, 7)).orElseThrow());
            world.setBlock(new BlockPos(8, 1, 8), id, 8);
            assertTrue(interaction
                    .remove(PlayerController.Mode.SURVIVAL, 8.5, 1.5, 5, 0, 0, 1)
                    .orElseThrow()
                    .changed());
        }
    }

    @Test
    void plantPlacementRequiresKnownGrassSupportAndDoesNotCollideWithPlayer() {
        World world = worldWithBlock(new BlockPos(8, 0, 8));
        var interaction = new BlockInteraction(world, 8);
        assertTrue(interaction
                .place(8.5, 3, 8.5, 0, -1, 0, BlockId.POPPY, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                .isEmpty());
        world.setBlock(new BlockPos(8, 0, 8), BlockId.GRASS, 8);
        Aabb body = new Aabb(8.1, 1, 8.1, 8.9, 2.8, 8.9);
        assertTrue(interaction
                .place(8.5, 3, 8.5, 0, -1, 0, BlockId.POPPY, PlayerController.Mode.SURVIVAL, body)
                .orElseThrow()
                .changed());
        assertFalse(world.isSolid(new BlockPos(8, 1, 8)));
    }

    @ParameterizedTest
    @CsvSource({"11,8,8,1,0,0", "5,8,8,-1,0,0", "8,11,8,0,1,0", "8,5,8,0,-1,0", "8,8,11,0,0,1", "8,8,5,0,0,-1"})
    void placesOnEachFaceThenRemovesTheNearestBlock(int x, int y, int z, int dx, int dy, int dz) {
        BlockPos support = new BlockPos(x, y, z);
        BlockPos placed = new BlockPos(x - dx, y - dy, z - dz);
        World world = worldWithBlock(support);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        long revision = world.getChunk(new ChunkPos(0, 0, 0)).revision();

        BlockChangeResult placement = interaction
                .place(8.5, 8.5, 8.5, dx, dy, dz, BlockId.DIRT, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                .orElseThrow();

        assertTrue(placement.changed());
        assertEquals(BlockId.AIR, placement.previous().orElseThrow());
        assertEquals(revision + 1, placement.chunkRevision());
        assertEquals(List.of(new ChunkPos(0, 0, 0)), placement.dirtyChunks());
        assertEquals(BlockId.DIRT, world.getBlock(placed).orElseThrow());
        assertEquals(BlockId.STONE, world.getBlock(support).orElseThrow());
        assertTrue(world.isSolid(placed));

        BlockChangeResult removal = interaction
                .remove(PlayerController.Mode.SURVIVAL, 8.5, 8.5, 8.5, dx, dy, dz)
                .orElseThrow();

        assertTrue(removal.changed());
        assertEquals(BlockId.DIRT, removal.previous().orElseThrow());
        assertEquals(revision + 2, removal.chunkRevision());
        assertFalse(world.isSolid(placed));
        assertEquals(BlockId.STONE, world.getBlock(support).orElseThrow());
        assertEquals(BlockId.AIR, world.changes().get(placed).orElseThrow());
        assertEquals(1, world.changes().size());
    }

    @ParameterizedTest
    @CsvSource({"16,14.5,1,15,0,1", "-2,0.5,-1,-1,-1,0"})
    void crossesChunkBoundariesAndReturnsBothDirtyNeighbors(
            int hitX, double originX, int directionX, int placedX, int firstChunkX, int secondChunkX) {
        World world = worldWithBlock(new BlockPos(hitX, 1, 1));
        BlockInteraction interaction = new BlockInteraction(world, 8);

        BlockChangeResult result = interaction
                .place(originX, 1.5, 1.5, directionX, 0, 0, BlockId.GRASS, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                .orElseThrow();

        assertTrue(result.changed());
        assertEquals(List.of(new ChunkPos(firstChunkX, 0, 0), new ChunkPos(secondChunkX, 0, 0)), result.dirtyChunks());
        BlockPos placed = new BlockPos(placedX, 1, 1);
        BlockPos local = Coordinates.worldToLocal(placed);
        assertEquals(
                BlockId.GRASS, world.getChunk(Coordinates.worldToChunk(placed)).get(local.x(), local.y(), local.z()));
    }

    @ParameterizedTest
    @CsvSource({
        "0.5,1,1,false",
        "1.25,1,1,false",
        "1.3,1,1,true",
        "0.5,2,1,true",
        "0.5,-0.8,1,true",
        "0.5,-0.79,1,false",
        "0.5,1,0.7,true",
        "0.5,1,0.71,false"
    })
    void survivalRejectsBodyOverlapButAllowsTouchingFaces(double x, double y, double z, boolean allowed) {
        World world = worldWithBlock(new BlockPos(1, 1, 1));
        BlockInteraction interaction = new BlockInteraction(world, 8);
        Chunk chunk = world.getChunk(new ChunkPos(0, 0, 0));
        long revision = chunk.revision();
        long dependency = chunk.meshDependencyRevision();

        var result = interaction.place(
                .5,
                1.5,
                1.5,
                1,
                0,
                0,
                BlockId.STONE,
                PlayerController.Mode.SURVIVAL,
                PlayerState.at(new PlayerState.Position(x, y, z)).body());

        assertEquals(allowed, result.isPresent());
        assertEquals(
                allowed ? BlockId.STONE : BlockId.AIR,
                world.getBlock(new BlockPos(0, 1, 1)).orElseThrow());
        assertEquals(revision + (allowed ? 1 : 0), chunk.revision());
        assertEquals(dependency + (allowed ? 1 : 0), chunk.meshDependencyRevision());
        assertEquals(allowed ? 1 : 0, world.changes().size());
        assertEquals(allowed ? 1 : 0, world.changes().drainDirtyTiles().size());
    }

    @Test
    void replacesHitWaterInsteadOfPlacingBesideItAndCanRemoveTheReplacement() {
        BlockPos target = new BlockPos(-1, 16, -1);
        World world = worldWithBlock(target);
        world.getChunk(Coordinates.worldToChunk(target)).set(15, 0, 15, BlockId.WATER);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        var change = interaction
                .place(-.5, 19, -.5, 0, -1, 0, BlockId.STONE, PlayerController.Mode.CREATIVE, DISTANT_BODY)
                .orElseThrow();
        assertEquals(BlockId.WATER, change.previous().orElseThrow());
        assertEquals(BlockId.STONE, world.getBlock(target).orElseThrow());
        assertEquals(BlockId.AIR, world.getBlock(new BlockPos(-1, 17, -1)).orElseThrow());
        assertEquals(8, change.dirtyChunks().size());
        var removed = interaction
                .remove(PlayerController.Mode.CREATIVE, -.5, 19, -.5, 0, -1, 0)
                .orElseThrow();
        assertEquals(BlockId.STONE, removed.previous().orElseThrow());
        assertEquals(BlockId.AIR, world.getBlock(target).orElseThrow());
    }

    @Test
    void waterPlacementAllowsBodyOverlapButReplacingItWithStoneDoesNot() {
        World world = worldWithBlock(new BlockPos(1, 1, 1));
        BlockInteraction interaction = new BlockInteraction(world, 8);
        Aabb body = PlayerState.at(new PlayerState.Position(.5, 1, 1.5)).body();
        var water = interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.WATER, PlayerController.Mode.CREATIVE, body)
                .orElseThrow();
        assertTrue(water.changed());
        assertEquals(BlockId.WATER, world.getBlock(new BlockPos(0, 1, 1)).orElseThrow());
        assertFalse(world.isSolid(new BlockPos(0, 1, 1)));
        assertTrue(interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.STONE, PlayerController.Mode.CREATIVE, body)
                .isEmpty());
        var sameWater = interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.WATER, PlayerController.Mode.CREATIVE, body)
                .orElseThrow();
        assertFalse(sameWater.changed());
        assertEquals(1, world.changes().size());
        assertTrue(interaction
                .remove(PlayerController.Mode.CREATIVE, .5, 1.5, 1.5, 1, 0, 0)
                .orElseThrow()
                .changed());
    }

    @Test
    void unloadedWaterDeltaCannotBeEdited() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        BlockPos target = new BlockPos(16, 1, 1);
        world.setBlock(target, BlockId.WATER, 8);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        assertTrue(interaction
                .place(15.5, 1.5, 1.5, 1, 0, 0, BlockId.STONE, PlayerController.Mode.CREATIVE, DISTANT_BODY)
                .isEmpty());
        assertTrue(interaction
                .remove(PlayerController.Mode.CREATIVE, 15.5, 1.5, 1.5, 1, 0, 0)
                .isEmpty());
        assertEquals(BlockId.WATER, world.changes().get(target).orElseThrow());
        assertEquals(1, world.residentCount());
    }

    @ParameterizedTest
    @CsvSource({"SURVIVAL,18.5", "SURVIVAL,16.5", "CREATIVE,18.5", "CREATIVE,16.5", "SPECTATOR,18.5", "SPECTATOR,16.5"})
    void onlyCreativeRemovesWaterWhileSurvivalCanRemoveTheGroundBelow(PlayerController.Mode mode, double originY) {
        BlockPos water = new BlockPos(-1, 16, -1);
        BlockPos ground = new BlockPos(-1, 15, -1);
        World world = worldWithBlock(ground);
        Chunk wetChunk = new Chunk();
        wetChunk.set(15, 0, 15, BlockId.WATER);
        world.putChunk(new ChunkPos(-1, 1, -1), wetChunk);
        long revision = wetChunk.revision();
        var result = new BlockInteraction(world, 8).remove(mode, -.5, originY, -.5, 0, -1, 0);
        assertEquals(mode != PlayerController.Mode.SPECTATOR, result.isPresent());
        assertEquals(
                mode == PlayerController.Mode.CREATIVE ? BlockId.AIR : BlockId.WATER,
                world.getBlock(water).orElseThrow());
        assertEquals(
                mode == PlayerController.Mode.SURVIVAL ? BlockId.AIR : BlockId.STONE,
                world.getBlock(ground).orElseThrow());
        assertEquals(revision + (mode == PlayerController.Mode.CREATIVE ? 1 : 0), wetChunk.revision());
        assertEquals(
                mode == PlayerController.Mode.SPECTATOR ? 0 : 1, world.changes().size());
        if (result.isPresent())
            assertEquals(
                    mode == PlayerController.Mode.CREATIVE ? BlockId.WATER : BlockId.STONE,
                    result.orElseThrow().previous().orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"SURVIVAL,.5", "SURVIVAL,1.5", "CREATIVE,.5", "CREATIVE,1.5", "SPECTATOR,.5", "SPECTATOR,1.5"})
    void placementCanDisplaceWaterAgainstGroundInSurvivalAndCreative(PlayerController.Mode mode, double originX) {
        World world = worldWithBlock(new BlockPos(2, 1, 1));
        Chunk chunk = world.getChunk(new ChunkPos(0, 0, 0));
        chunk.set(1, 1, 1, BlockId.WATER);
        long revision = chunk.revision(), dependency = chunk.meshDependencyRevision();
        var result = new BlockInteraction(world, 8).place(originX, 1.5, 1.5, 1, 0, 0, BlockId.DIRT, mode, DISTANT_BODY);
        boolean allowed = mode != PlayerController.Mode.SPECTATOR;
        assertEquals(allowed, result.isPresent());
        assertEquals(
                allowed ? BlockId.DIRT : BlockId.WATER,
                world.getBlock(new BlockPos(1, 1, 1)).orElseThrow());
        assertEquals(BlockId.STONE, world.getBlock(new BlockPos(2, 1, 1)).orElseThrow());
        assertEquals(revision + (allowed ? 1 : 0), chunk.revision());
        assertEquals(dependency + (allowed ? 1 : 0), chunk.meshDependencyRevision());
        assertEquals(allowed ? 1 : 0, world.changes().size());
        assertEquals(allowed ? 1 : 0, world.changes().drainDirtyTiles().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void survivalDisplacesEveryWaterStateButDoesNotMineIt(int state) {
        World world = worldWithBlock(new BlockPos(2, 1, 1));
        BlockPos wet = new BlockPos(1, 1, 1);
        world.setBlock(wet, BlockId.WATER, (byte) state, 8);
        var notifications = new ArrayList<BlockPos>();
        BlockInteraction interaction = new BlockInteraction(world, 8, notifications::add);
        var overlap = new Aabb(1, 1, 1, 2, 2, 2);
        assertTrue(interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.STONE, PlayerController.Mode.SURVIVAL, overlap)
                .isEmpty());
        assertEquals(state, world.getWaterState(wet).orElseThrow());
        assertTrue(notifications.isEmpty());
        var placed = interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.STONE, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                .orElseThrow();
        assertEquals(BlockId.WATER, placed.previous().orElseThrow());
        assertTrue(placed.changed());
        assertEquals(WaterState.EMPTY, world.getWaterState(wet).orElseThrow());
        assertEquals(List.of(wet), notifications);
        world.setBlock(wet, BlockId.WATER, (byte) state, 8);
        var mined = interaction
                .remove(PlayerController.Mode.SURVIVAL, .5, 1.5, 1.5, 1, 0, 0)
                .orElseThrow();
        assertEquals(BlockId.STONE, mined.previous().orElseThrow());
        assertEquals(state, world.getWaterState(wet).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(
            value = PlayerController.Mode.class,
            names = {"SURVIVAL", "SPECTATOR"})
    void targetingOnlyWaterDoesNotEditOrDirtyTheWorld(PlayerController.Mode mode) {
        World world = worldWithBlock(new BlockPos(1, 1, 1));
        Chunk chunk = world.getChunk(new ChunkPos(0, 0, 0));
        chunk.set(1, 1, 1, BlockId.WATER);
        long revision = chunk.revision(), dependency = chunk.meshDependencyRevision();
        BlockInteraction interaction = new BlockInteraction(world, 8);
        assertTrue(interaction.remove(mode, .5, 1.5, 1.5, 1, 0, 0).isEmpty());
        assertTrue(interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.STONE, mode, DISTANT_BODY)
                .isEmpty());
        assertEquals(BlockId.WATER, chunk.get(1, 1, 1));
        assertEquals(revision, chunk.revision());
        assertEquals(dependency, chunk.meshDependencyRevision());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().drainDirtyTiles().isEmpty());
    }

    @Test
    void survivalCanPlaceInAnAirGapBehindWaterWithoutReplacingWater() {
        World world = worldWithBlock(new BlockPos(3, 1, 1));
        world.getChunk(new ChunkPos(0, 0, 0)).set(1, 1, 1, BlockId.WATER);
        var result = new BlockInteraction(world, 8)
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.DIRT, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                .orElseThrow();
        assertTrue(result.changed());
        assertEquals(BlockId.WATER, world.getBlock(new BlockPos(1, 1, 1)).orElseThrow());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(2, 1, 1)).orElseThrow());
        assertEquals(BlockId.STONE, world.getBlock(new BlockPos(3, 1, 1)).orElseThrow());
    }

    @Test
    void spectatorCannotPlaceOrRemoveEvenInsideItsPosition() {
        World world = worldWithBlock(new BlockPos(1, 1, 1));
        BlockInteraction interaction = new BlockInteraction(world, 8);

        assertTrue(interaction
                .place(
                        .5,
                        1.5,
                        1.5,
                        1,
                        0,
                        0,
                        BlockId.STONE,
                        PlayerController.Mode.SPECTATOR,
                        PlayerState.at(new PlayerState.Position(.5, 1, 1.5)).body())
                .isEmpty());
        assertTrue(interaction
                .remove(PlayerController.Mode.SPECTATOR, .5, 1.5, 1.5, 1, 0, 0)
                .isEmpty());
        assertEquals(0, world.changes().size());
        assertEquals(1, world.getChunk(new ChunkPos(0, 0, 0)).revision());
    }

    @Test
    void startInsideSolidCannotPlaceButCanRemove() {
        BlockPos block = new BlockPos(0, 1, 1);
        World world = worldWithBlock(block);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        long revision = world.getChunk(new ChunkPos(0, 0, 0)).revision();

        assertTrue(interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.DIRT, PlayerController.Mode.CREATIVE, DISTANT_BODY)
                .isEmpty());
        assertEquals(BlockId.STONE, world.getBlock(block).orElseThrow());
        assertEquals(revision, world.getChunk(new ChunkPos(0, 0, 0)).revision());
        assertEquals(0, world.changes().size());
        assertTrue(interaction
                .remove(PlayerController.Mode.CREATIVE, .5, 1.5, 1.5, 1, 0, 0)
                .orElseThrow()
                .changed());
        assertEquals(BlockId.AIR, world.getBlock(block).orElseThrow());
    }

    @Test
    void ignoresAirSelectionAndRejectsInvalidInputsWithoutEdits() {
        World world = worldWithBlock(new BlockPos(1, 1, 1));
        BlockInteraction interaction = new BlockInteraction(world, 8);
        long revision = world.getChunk(new ChunkPos(0, 0, 0)).revision();

        assertTrue(interaction
                .place(.5, 1.5, 1.5, 1, 0, 0, BlockId.AIR, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> interaction.place(
                        .5, 1.5, 1.5, 1, 0, 0, (byte) 255, PlayerController.Mode.SURVIVAL, DISTANT_BODY));
        assertThrows(
                IllegalArgumentException.class,
                () -> interaction.place(
                        .5,
                        1.5,
                        1.5,
                        1,
                        0,
                        0,
                        BlockId.DIRT,
                        PlayerController.Mode.SURVIVAL,
                        PlayerState.at(new PlayerState.Position(Double.NaN, 0, 0))
                                .body()));
        assertEquals(revision, world.getChunk(new ChunkPos(0, 0, 0)).revision());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().drainDirtyTiles().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,true", "-0.001,false"})
    void limitsRemovalAndPlacementToSixBlocks(double originX, boolean reachable) {
        World world = worldWithBlock(new BlockPos(6, 1, 1));
        world.putChunk(new ChunkPos(-1, 0, 0), new Chunk());
        BlockInteraction interaction = new BlockInteraction(world, 8);

        assertEquals(
                reachable,
                interaction
                        .place(originX, 1.5, 1.5, 1, 0, 0, BlockId.DIRT, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                        .isPresent());
        if (reachable) interaction.remove(PlayerController.Mode.SURVIVAL, originX, 1.5, 1.5, 1, 0, 0);
        assertEquals(
                reachable,
                interaction
                        .remove(PlayerController.Mode.SURVIVAL, originX, 1.5, 1.5, 1, 0, 0)
                        .isPresent());
        assertEquals(
                reachable ? BlockId.AIR : BlockId.STONE,
                world.getBlock(new BlockPos(6, 1, 1)).orElseThrow());
        assertEquals(reachable ? 2 : 0, world.changes().size());
    }

    @Test
    void missingTerrainBlocksEditingEvenWithSavedDeltasBeyondTheBoundary() {
        World world = worldWithBlock(new BlockPos(16, 1, 1));
        world.removeChunk(new ChunkPos(1, 0, 0));
        world.setBlock(new BlockPos(16, 1, 1), BlockId.STONE, 8);
        world.changes().drainDirtyTiles();
        BlockInteraction interaction = new BlockInteraction(world, 8);

        assertTrue(interaction
                .remove(PlayerController.Mode.SURVIVAL, 15.5, 1.5, 1.5, 1, 0, 0)
                .isEmpty());
        assertTrue(interaction
                .place(15.5, 1.5, 1.5, 1, 0, 0, BlockId.DIRT, PlayerController.Mode.SURVIVAL, DISTANT_BODY)
                .isEmpty());
        assertTrue(interaction
                .remove(PlayerController.Mode.SURVIVAL, 16.5, 1.5, 1.5, 1, 0, 0)
                .isEmpty());
        assertEquals(1, world.residentCount());
        assertEquals(0, world.getChunk(new ChunkPos(0, 0, 0)).revision());
        assertEquals(1, world.changes().size());
        assertEquals(BlockId.STONE, world.changes().get(new BlockPos(16, 1, 1)).orElseThrow());
        assertTrue(world.changes().drainDirtyTiles().isEmpty());
        assertFalse(world.isChunkRequested(new ChunkPos(1, 0, 0)));
    }

    @Test
    void creativePlacementCannotEmbedThePlayerWhileRemovalIsAllowed() {
        World world = worldWithBlock(new BlockPos(1, 1, 1));
        BlockInteraction interaction = new BlockInteraction(world, 8);
        assertTrue(interaction
                .place(
                        .5,
                        1.5,
                        1.5,
                        1,
                        0,
                        0,
                        BlockId.DIRT,
                        PlayerController.Mode.CREATIVE,
                        PlayerState.at(new PlayerState.Position(.5, 1, 1.5)).body())
                .isEmpty());
        assertEquals(0, world.changes().size());
        assertTrue(interaction
                .remove(PlayerController.Mode.CREATIVE, .5, 1.5, 1.5, 1, 0, 0)
                .orElseThrow()
                .changed());
    }

    @ParameterizedTest
    @CsvSource({
        "SURVIVAL,0.4,true",
        "SURVIVAL,0.5,true",
        "SURVIVAL,0.500001,false",
        "CREATIVE,0.4,true",
        "CREATIVE,0.5,true",
        "CREATIVE,0.500001,false"
    })
    void placementUsesTheCurrentPoseAndAllowsHeadFaceContact(
            PlayerController.Mode mode, double feetY, boolean allowed) {
        World world = worldWithBlock(new BlockPos(1, 2, 1));
        BlockInteraction interaction = new BlockInteraction(world, 8);
        PlayerState standing = PlayerState.at(new PlayerState.Position(.5, feetY, 1.5));
        assertTrue(interaction
                .place(.5, 2.5, 1.5, 1, 0, 0, BlockId.DIRT, mode, standing.body())
                .isEmpty());
        assertEquals(0, world.changes().size());
        PlayerState crouched = standing.withCrouching(true);
        assertEquals(
                allowed,
                interaction
                        .place(.5, 2.5, 1.5, 1, 0, 0, BlockId.DIRT, mode, crouched.body())
                        .isPresent());
        assertEquals(
                allowed ? BlockId.DIRT : BlockId.AIR,
                world.getBlock(new BlockPos(0, 2, 1)).orElseThrow());
        assertEquals(allowed ? 1 : 0, world.changes().size());
        assertEquals(allowed ? 2 : 1, world.getChunk(new ChunkPos(0, 0, 0)).revision());
    }

    private static World worldWithBlock(BlockPos position) {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        Chunk chunk = new Chunk();
        BlockPos local = Coordinates.worldToLocal(position);
        chunk.set(local.x(), local.y(), local.z(), BlockId.STONE);
        world.putChunk(Coordinates.worldToChunk(position), chunk);
        return world;
    }
}
