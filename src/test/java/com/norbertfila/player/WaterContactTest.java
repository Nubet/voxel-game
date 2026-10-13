package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class WaterContactTest {
    @Test
    void thinLayerHasPartialVolumeInsteadOfWholeBlockImmersion() {
        World world = world();
        layer(world, 0, WaterState.flowing(7));
        var body = new Aabb(.2, 0, .2, .8, 1, .8);
        var contact = WaterContact.sample(world, body, new PlayerState.Position(.5, .5, .5));
        assertEquals(1 / 9d, contact.submergedFraction(), 1e-7);
        assertFalse(contact.eyesSubmerged());
        assertTrue(contact.available());
    }

    @Test
    void sourceLakeAndStackedWaterAgreeWithMeshHeights() {
        World world = world();
        layer(world, 0, WaterState.SOURCE);
        var player = PlayerState.at(new PlayerState.Position(.5, 0, .5));
        assertEquals(
                (8 / 9d) / player.height(), WaterContact.sample(world, player).submergedFraction(), 1e-7);
        layer(world, 1, WaterState.SOURCE);
        assertEquals(1, WaterContact.sample(world, player).submergedFraction(), 1e-7);
        assertTrue(WaterContact.sample(world, player).eyesSubmerged());
    }

    @Test
    void eyesAtSurfaceUseStrictBoundaryAndCrouchingUsesCurrentPose() {
        World world = world();
        layer(world, 0, WaterState.SOURCE);
        var neighborhood = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        double height = WaterSurface.corners(neighborhood::waterSample, 0, 0, 0).heightAt(.5, .5);
        Aabb body = new Aabb(.2, 0, .2, .8, 1, .8);
        assertFalse(WaterContact.sample(world, body, new PlayerState.Position(.5, height, .5))
                .eyesSubmerged());
        assertFalse(WaterContact.sample(world, body, new PlayerState.Position(.5, height + 1e-5, .5))
                .eyesSubmerged());
        assertTrue(WaterContact.sample(world, body, new PlayerState.Position(.5, height - 1e-5, .5))
                .eyesSubmerged());
        var standing = PlayerState.at(new PlayerState.Position(.5, -.5, .5));
        var crouching = standing.withCrouching(true);
        assertFalse(WaterContact.sample(world, standing).eyesSubmerged());
        assertTrue(WaterContact.sample(world, crouching).eyesSubmerged());
        assertTrue(WaterContact.sample(world, crouching).submergedFraction()
                > WaterContact.sample(world, standing).submergedFraction());
    }

    @Test
    void slopeVolumeIsClippedAgainstTheActualTriangles() {
        World world = world();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                world.setBlock(new BlockPos(x, 0, z), BlockId.WATER, WaterState.flowing(3 + x), 64);
        var n = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        var heights = WaterSurface.corners(n::waterSample, 0, 0, 0);
        double low = (heights.h00() + heights.h10()) / 2d;
        double width = (heights.h00() - low) / (heights.h00() - heights.h10());
        double expectedVolume = width * (heights.h00() - low) / 2;
        var body = new Aabb(0, low, 0, 1, 1, 1);
        var contact = WaterContact.sample(world, body, new PlayerState.Position(.25, low, .5));
        assertEquals(expectedVolume / (1 - low), contact.submergedFraction(), 1e-7);
        assertTrue(contact.eyesSubmerged());
        assertFalse(WaterContact.sample(world, body, new PlayerState.Position(.75, low, .5))
                .eyesSubmerged());
        var narrow = new Aabb(.1, 0, .2, .4, 1, .8);
        assertEquals(
                heights.heightAt(.25, .5),
                WaterContact.sample(world, narrow, new PlayerState.Position(.25, .9, .5))
                        .submergedFraction(),
                1e-7);
    }

    @Test
    void dryAndFaceTouchingBodiesDoNotIntersectWater() {
        World world = world();
        layer(world, 0, WaterState.SOURCE);
        assertEquals(
                0,
                WaterContact.sample(world, new Aabb(.2, 1, .2, .8, 2, .8), new PlayerState.Position(.5, 1.62, .5))
                        .submergedFraction());
        assertEquals(
                0,
                WaterContact.sample(world, new Aabb(2, 0, 2, 3, 1, 3), new PlayerState.Position(2.5, .5, 2.5))
                        .submergedFraction());
    }

    @Test
    void unloadedOverlayAndMissingDiagonalAreUnavailableWithoutGeneration() {
        World world = new World();
        BlockPos position = new BlockPos(-1, 0, -1);
        world.setBlock(position, BlockId.WATER, 64);
        var player = PlayerState.at(new PlayerState.Position(-.5, 0, -.5));
        var absent = WaterContact.sample(world, player);
        assertFalse(absent.available());
        assertEquals(0, absent.submergedFraction());
        world.putChunk(new ChunkPos(-1, 0, -1), new Chunk());
        var partial = WaterContact.sample(world, player);
        assertFalse(partial.available());
        assertTrue(partial.submergedFraction() > 0);
        assertEquals(1, world.residentCount());
        assertFalse(world.isChunkRequested(new ChunkPos(0, 0, 0)));
    }

    @Test
    void negativeBoundaryUsesSameCornerValuesAsBothChunkMeshes() {
        World world = world();
        for (int x = -2; x <= 1; x++)
            for (int z = -2; z <= 1; z++)
                world.setBlock(new BlockPos(x, 0, z), BlockId.WATER, WaterState.flowing(2), 64);
        var player = PlayerState.at(new PlayerState.Position(0, 0, 0));
        assertEquals(
                (6 / 9d) / player.height(), WaterContact.sample(world, player).submergedFraction(), 1e-7);
        assertTrue(WaterContact.sample(world, player).available());
    }

    @Test
    void worldLimitDoesNotWrapAndOversizedQueriesAreRejected() {
        World world = new World();
        int x = Integer.MAX_VALUE;
        BlockPos position = new BlockPos(x, 0, 0);
        world.putChunk(Coordinates.worldToChunk(position), new Chunk());
        world.setBlock(position, BlockId.WATER, 64);
        var body = new Aabb(x + .1, 0, .1, x + .9, 1, .9);
        var result = WaterContact.sample(world, body, new PlayerState.Position(x + .5, .5, .5));
        assertTrue(result.submergedFraction() > 0);
        assertEquals(1, world.residentCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> WaterContact.sample(
                        world, new Aabb(0, 0, 0, 1000, 1000, 1000), new PlayerState.Position(0, 0, 0)));
    }

    private static World world() {
        World world = new World();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) world.putChunk(new ChunkPos(x, y, z), new Chunk());
        return world;
    }

    private static void layer(World world, int y, byte state) {
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) world.setBlock(new BlockPos(x, y, z), BlockId.WATER, state, 64);
    }
}
