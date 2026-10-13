package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class PlayerSwimmingTest {
    private static final double DT = 1d / 60;
    private final PlayerPhysics physics = new PlayerPhysics();
    private static final WaterContact.Result WET = new WaterContact.Result(1, true, true);

    @Test
    void dryPathIsIdenticalAndUnknownContactPreservesState() {
        World world = known();
        var start = new PlayerState(new PlayerState.Position(4, 4, 4), 3, 2, -1, true, 0, false);
        assertEquals(
                physics.step(world, start, 1, 1, true, true, 40, DT),
                physics.step(world, start, 1, 1, true, false, true, 40, DT, new WaterContact.Result(0, false, true)));
        assertSame(start, step(world, start, false, false, new WaterContact.Result(.5, false, false)));
    }

    @Test
    void heldControlsAscendDescendCancelAndDoNotRepeatGroundJump() {
        World world = known();
        var start = new PlayerState(new PlayerState.Position(4, 4, 4), 0, 0, 0, true, 0, false);
        PlayerState up = start, down = start, idle = start, both = start;
        for (int tick = 0; tick < 60; tick++) {
            up = step(world, up, true, false, WET);
            down = step(world, down, false, true, WET);
            idle = step(world, idle, false, false, WET);
            both = step(world, both, true, true, WET);
        }
        assertTrue(up.velocityY() > 3 && up.velocityY() < 3.5);
        assertTrue(down.velocityY() < -4);
        assertTrue(idle.velocityY() < 0 && idle.velocityY() > -.51);
        assertEquals(idle, both);
        assertEquals(0, up.jumpCooldownSeconds());
    }

    @Test
    void dragAndCurrentScaleWithVolumeAndRemainIndependentOfTimeStep() {
        World world = known();
        var start = new PlayerState(new PlayerState.Position(4, 8, 4), 5, 0, 0, false, 0, false);
        var partial = new WaterContact.Result(.5, false, true);
        assertTrue(step(world, start, false, false, WET).velocityX()
                < step(world, start, false, false, partial).velocityX());
        var current = new WaterContact.Result(1, true, true, 1, 0, 0);
        var still = PlayerState.at(start.position());
        double partialDrag = (PlayerPhysics.WATER_DRAG - Math.log(.91) / .05) / 2;
        assertEquals(
                2 * (-Math.expm1(-partialDrag * DT) / partialDrag),
                step(world, still, false, false, new WaterContact.Result(.5, false, true, 1, 0, 0))
                        .velocityX(),
                1e-12);
        assertEquals(
                4 * (-Math.expm1(-PlayerPhysics.WATER_DRAG * DT) / PlayerPhysics.WATER_DRAG),
                step(world, still, false, false, current).velocityX(),
                1e-12);
        PlayerState coarse = physics.step(world, start, 0, 0, false, false, false, 0, .05, current);
        PlayerState fine = start;
        for (int i = 0; i < 3; i++) fine = step(world, fine, false, false, current);
        assertEquals(coarse.velocityX(), fine.velocityX(), 1e-12);
        assertEquals(coarse.velocityY(), fine.velocityY(), 1e-12);
    }

    @Test
    void floorWallAndCeilingResolveWithoutPenetration() {
        World world = known();
        for (int x = 0; x < 8; x++)
            for (int z = 0; z < 8; z++) {
                world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                world.setBlock(new BlockPos(x, 5, z), BlockId.STONE, 64);
            }
        for (int y = 1; y < 5; y++)
            for (int z = 0; z < 8; z++) world.setBlock(new BlockPos(6, y, z), BlockId.STONE, 64);
        PlayerState down = PlayerState.at(new PlayerState.Position(4, 2, 4)), up = down, side = down;
        for (int tick = 0; tick < 240; tick++) {
            down = step(world, down, false, true, WET);
            up = step(world, up, true, false, WET);
            side = physics.step(world, side, 1, 0, false, false, false, 0, DT, WET);
        }
        assertEquals(1, down.position().y());
        assertTrue(down.grounded());
        assertEquals(0, down.velocityY());
        assertTrue(up.body().maxY() <= 5);
        assertEquals(3.2, up.position().y(), 1e-12);
        assertEquals(0, up.velocityY());
        assertEquals(5.7, side.position().x(), 1e-12);
        assertEquals(0, side.velocityX());
    }

    @Test
    void contactWeightsCurrentsByVolumeRatherThanCellCount() {
        World world = known();
        for (int x = 2; x <= 6; x++)
            for (int y = 2; y <= 6; y++)
                for (int z = 2; z <= 6; z++)
                    world.setBlock(new BlockPos(x, y, z), BlockId.WATER, WaterState.FALLING, 64);
        var small =
                WaterContact.sample(world, new Aabb(3.1, 3, 3.1, 3.9, 4, 3.9), new PlayerState.Position(3.5, 3.5, 3.5));
        var large =
                WaterContact.sample(world, new Aabb(3.1, 3, 3.1, 4.9, 5, 4.9), new PlayerState.Position(3.5, 3.5, 3.5));
        assertEquals(1, small.submergedFraction());
        assertEquals(small.currentY(), large.currentY(), 1e-12);
        assertEquals(-1, large.currentY());
    }

    @Test
    void modesGesturesAndPredictionUseTheSameWaterMotion() {
        World world = lake();
        PlayerController survival = controller(world, PlayerController.Mode.SURVIVAL);
        PlayerController creative = controller(world, PlayerController.Mode.CREATIVE);
        var forward = new PlayerInput(true, false, false, false, false, false, false);
        for (int i = 0; i < 30; i++) {
            var required = survival.requiredCollisionChunks(world, forward, DT, 0, 0);
            assertEquals(required, survival.requiredCollisionChunks(world, forward, DT, 0, 0));
            survival.tick(world, forward, DT, 0, 0);
            creative.tick(world, forward, DT, 0, 0);
            assertEquals(survival.state(), creative.state());
            assertTrue(required.containsAll(
                    VoxelCollision.requiredChunks(survival.state().body())));
        }
        creative.tick(world, press(1), DT, 0, 0);
        creative.tick(world, press(1.2), DT, 0, 0);
        assertTrue(creative.flying());
        var flying = creative.state();
        creative.tick(world, forward, DT, 0, 0);
        assertEquals(physics.fly(world, flying, 1, 0, 0, false, DT), creative.state());
        creative.setMode(world, PlayerController.Mode.SPECTATOR);
        assertTrue(creative.requiredCollisionChunks(world, forward, DT, 0, 0).isEmpty());
        var before = creative.state().position();
        creative.tick(null, forward, DT, 0, 0);
        assertEquals(before.x() + 12 * DT, creative.state().position().x());
    }

    @Test
    void missingSurfaceHaloPausesOnlyMovementAndIsRequested() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        world.setBlock(new BlockPos(0, 3, 0), BlockId.WATER, 64);
        PlayerController player = controller(world, PlayerController.Mode.SURVIVAL);
        player.setPosition(new PlayerState.Position(.5, 3, .5));
        var before = player.state();
        var required = player.requiredCollisionChunks(world, PlayerInput.NONE, DT, 0, 0);
        assertTrue(required.contains(new ChunkPos(-1, 0, -1)));
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertSame(before, player.state());
        for (ChunkPos chunk : required) if (!world.hasChunk(chunk)) world.putChunk(chunk, new Chunk());
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertNotEquals(before, player.state());
    }

    @Test
    void shallowShorePreservesMomentumAndCrouchedSurfaceControl() {
        World world = known();
        for (int x = -4; x < 12; x++)
            for (int z = -4; z <= 4; z++) {
                world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                if (x < 4) world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, WaterState.flowing(7), 64);
            }
        var player = controller(world, PlayerController.Mode.SURVIVAL);
        player.setPosition(new PlayerState.Position(2, 1, .5));
        var forward = new PlayerInput(true, false, false, false, false, false, false);
        double previousX = 2;
        boolean leftWater = false;
        for (int tick = 0; tick < 120; tick++) {
            var before = WaterContact.sample(world, player.state());
            player.tick(world, forward, DT, 0, 0);
            assertTrue(player.state().position().x() > previousX);
            assertTrue(player.state().position().x() - previousX < .1);
            if (before.submergedFraction() == 0) {
                leftWater = true;
                assertTrue(player.state().velocityX() > 0);
            }
            previousX = player.state().position().x();
        }
        assertTrue(leftWater);
        World deep = lake();
        var crouched = controller(deep, PlayerController.Mode.SURVIVAL);
        var sneak = new PlayerInput(false, false, false, false, false, true, false);
        crouched.tick(deep, sneak, DT, 0, 0);
        assertTrue(crouched.state().crouching());
        assertTrue(crouched.state().velocityY() < 0);
    }

    private PlayerState step(World world, PlayerState start, boolean jump, boolean sneak, WaterContact.Result water) {
        return physics.step(world, start, 0, 0, jump, sneak, false, 0, DT, water);
    }

    private static PlayerInput press(double time) {
        return new PlayerInput(false, false, false, false, false, false, false, true, time);
    }

    private static PlayerController controller(World world, PlayerController.Mode mode) {
        var player = new PlayerController();
        player.setPosition(new PlayerState.Position(4, 3, 4));
        assertTrue(player.setMode(world, mode));
        return player;
    }

    private static World known() {
        World world = new World();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) world.putChunk(new ChunkPos(x, y, z), new Chunk());
        return world;
    }

    private static World lake() {
        World world = known();
        for (int x = 1; x <= 12; x++)
            for (int y = 1; y <= 8; y++)
                for (int z = 1; z <= 12; z++) world.setBlock(new BlockPos(x, y, z), BlockId.WATER, 64);
        return world;
    }
}
