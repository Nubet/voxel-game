package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import com.norbertfila.world.VoxelCollision;
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
    void dragScalesWithVolumeAndCurrentWithDepthIndependentlyOfTimeStep() {
        World world = known();
        var start = new PlayerState(new PlayerState.Position(4, 8, 4), 5, 0, 0, false, 0, false);
        var partial = new WaterContact.Result(.5, false, true);
        assertTrue(step(world, start, false, false, WET).velocityX()
                < step(world, start, false, false, partial).velocityX());
        var current = new WaterContact.Result(1, true, true, 1, 0, 0);
        var still = PlayerState.at(start.position());
        double partialDrag = (PlayerPhysics.WATER_DRAG - Math.log(.91) / .05) / 2;
        assertEquals(
                4 * (-Math.expm1(-partialDrag * DT) / partialDrag),
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
    void landingInShallowFlowCarriesPlayerDownstreamWithoutMovementInput() {
        for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int ticksPerSecond : new int[] {60, 120}) {
                World world = known();
                for (int along = 0; along <= 7; along++)
                    for (int across = -3; across <= 3; across++) {
                        int x = direction[0] * along - direction[1] * across;
                        int z = direction[1] * along + direction[0] * across;
                        world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                        world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, (byte) along, 64);
                    }
                var player = controller(world, PlayerController.Mode.SURVIVAL);
                var start = new PlayerState.Position(direction[0] * 6 + .5, 3, direction[1] * 6 + .5);
                player.setPosition(start);
                for (int tick = 0; tick < ticksPerSecond * 3; tick++) {
                    var required = player.requiredCollisionChunks(world, PlayerInput.NONE, 1d / ticksPerSecond, 0, 0);
                    player.tick(world, PlayerInput.NONE, 1d / ticksPerSecond, 0, 0);
                    assertTrue(required.containsAll(
                            VoxelCollision.requiredChunks(player.state().body())));
                    assertTrue(
                            new VoxelCollision().spaceFree(world, player.state().body()));
                }
                double downstream = (player.state().position().x() - start.x()) * direction[0]
                        + (player.state().position().z() - start.z()) * direction[1];
                double sideways = (player.state().position().z() - start.z()) * direction[0]
                        - (player.state().position().x() - start.x()) * direction[1];
                assertTrue(downstream > .25, "Shallow current barely moves the player: " + downstream);
                assertEquals(0, sideways, 1e-12);
                assertTrue(player.state().grounded());
            }
        }
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

    @Test
    void heldJumpLeavesOneBlockPoolAndClimbsItsFullBlockBank() {
        for (int depth : new int[] {1, 4}) {
            for (var mode :
                    new PlayerController.Mode[] {PlayerController.Mode.SURVIVAL, PlayerController.Mode.CREATIVE}) {
                World world = known();
                for (int x = -4; x <= 10; x++)
                    for (int z = -4; z <= 4; z++) {
                        world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                        for (int y = 1; y <= depth; y++)
                            world.setBlock(new BlockPos(x, y, z), x >= 4 ? BlockId.STONE : BlockId.WATER, 64);
                    }
                var player = controller(world, mode);
                player.setPosition(new PlayerState.Position(2.5, 1, .5));
                var jumpForward = new PlayerInput(true, false, false, false, true, false, false);
                boolean eyesAbove = false;
                double peak = 1;
                for (int tick = 0; tick < 360; tick++) {
                    var required = player.requiredCollisionChunks(world, jumpForward, DT, 0, 0);
                    player.tick(world, jumpForward, DT, 0, 0);
                    peak = Math.max(peak, player.state().position().y());
                    assertTrue(required.containsAll(
                            VoxelCollision.requiredChunks(player.state().body())));
                    assertTrue(
                            new VoxelCollision().spaceFree(world, player.state().body()));
                    eyesAbove |= !WaterContact.sample(world, player.state()).eyesSubmerged();
                    if (player.state().position().x() > 5) break;
                }
                assertTrue(eyesAbove, "Held jump must bring eyes above water");
                assertTrue(
                        player.state().position().x() > 5,
                        "Cannot climb bank of pool depth " + depth + " in " + mode + "; peak=" + peak + "; state="
                                + player.state());
                assertTrue(player.state().position().y() >= depth + 1, "Feet must clear the bank");
                assertFalse(player.state().crouching());
                assertFalse(player.flying());
            }
        }
    }

    @Test
    void heldJumpClimbsFullBlockBankAtEveryFlowingWaterLevel() {
        for (int level = 1; level <= WaterState.MAX_DISTANCE; level++) {
            for (var mode :
                    new PlayerController.Mode[] {PlayerController.Mode.SURVIVAL, PlayerController.Mode.CREATIVE}) {
                World world = known();
                for (int x = -4; x <= 10; x++)
                    for (int z = -4; z <= 4; z++) {
                        world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                        if (x >= 4) world.setBlock(new BlockPos(x, 1, z), BlockId.STONE, 64);
                        else world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, WaterState.flowing(level), 64);
                    }
                var player = controller(world, mode);
                player.setPosition(new PlayerState.Position(3.5, 1, .5));
                for (int tick = 0; tick < 30; tick++) player.tick(world, PlayerInput.NONE, DT, 0, 0);
                assertTrue(player.state().grounded());
                assertTrue(WaterContact.sample(world, player.state()).submergedFraction() > 0);
                var jump = new PlayerInput(true, false, false, false, true, false, false);
                for (int tick = 0; tick < 240 && player.state().position().x() <= 5; tick++) {
                    var required = player.requiredCollisionChunks(world, jump, DT, 0, 0);
                    player.tick(world, jump, DT, 0, 0);
                    assertTrue(required.containsAll(
                            VoxelCollision.requiredChunks(player.state().body())));
                    assertTrue(
                            new VoxelCollision().spaceFree(world, player.state().body()));
                }
                assertTrue(
                        player.state().position().x() > 5, "Cannot climb bank at water level " + level + " in " + mode);
                assertTrue(player.state().position().y() >= 2);
                assertFalse(player.flying());
            }
        }
    }

    @Test
    void shallowGroundJumpPreservesCurrentAndRespectsCooldownAndSneak() {
        World world = known();
        var shallow = new WaterContact.Result(.1, false, true, 1, 0, 0);
        for (boolean crouching : new boolean[] {false, true}) {
            var start = new PlayerState(new PlayerState.Position(4, 4, 4), 0, 0, 0, true, 0, crouching);
            var dry = physics.step(world, start, 0, 0, true, DT);
            var wet = step(world, start, true, false, shallow);
            assertEquals(dry.velocityY(), wet.velocityY());
            assertEquals(dry.jumpCooldownSeconds(), wet.jumpCooldownSeconds());
            assertTrue(wet.velocityX() > 0);
            var cooling = new PlayerState(start.position(), 0, 0, 0, true, .25, crouching);
            assertTrue(step(world, cooling, true, false, shallow).velocityY() < 1);
            assertTrue(step(world, wet, true, false, shallow).velocityY() < wet.velocityY());
            assertTrue(step(world, start, true, true, shallow).velocityY() < 0);
        }
    }

    @Test
    void partialImmersionStillAllowsHeldJumpToReachTheSurface() {
        World world = known();
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, 64);
            }
        var player = controller(world, PlayerController.Mode.SURVIVAL);
        player.setPosition(new PlayerState.Position(.5, 1, .5));
        var jump = new PlayerInput(false, false, false, false, true, false, false);
        double maximumFeet = 1;
        for (int tick = 0; tick < 180; tick++) {
            player.tick(world, jump, DT, 0, 0);
            maximumFeet = Math.max(maximumFeet, player.state().position().y());
        }
        assertTrue(maximumFeet > 2, "A one-block pool must not trap the feet below its bank height");
    }

    @Test
    void waterExitLiftDoesNotClimbTallWallsOrPassThroughLowCeilings() {
        for (boolean ceiling : new boolean[] {false, true}) {
            World world = known();
            for (int x = -3; x <= 8; x++)
                for (int z = -3; z <= 3; z++) {
                    world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                    world.setBlock(new BlockPos(x, 1, z), x >= 4 ? BlockId.STONE : BlockId.WATER, 64);
                    if (ceiling) world.setBlock(new BlockPos(x, 3, z), BlockId.STONE, 64);
                    else if (x >= 4)
                        for (int y = 2; y <= 4; y++) world.setBlock(new BlockPos(x, y, z), BlockId.STONE, 64);
                }
            var player = controller(world, PlayerController.Mode.SURVIVAL);
            player.setPosition(new PlayerState.Position(3.5, 1, .5));
            var jump = new PlayerInput(true, false, false, false, true, false, false);
            for (int tick = 0; tick < 240; tick++) {
                player.tick(world, jump, DT, 0, 0);
                assertTrue(player.state().body().maxX() <= 4);
                assertTrue(new VoxelCollision().spaceFree(world, player.state().body()));
                if (ceiling) assertTrue(player.state().body().maxY() <= 3);
            }
        }
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
