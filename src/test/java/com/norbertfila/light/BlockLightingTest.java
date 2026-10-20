package com.norbertfila.light;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class BlockLightingTest {
    private static final BlockPos FEET = new BlockPos(8, -10, 8), EYE = new BlockPos(8, -9, 8);

    @Test
    void placedTorchesLightTunnelsAndStopAtStone() {
        var world = LightProbeTest.groundBelowZero();
        LightProbeTest.carve(world, 0, -10, 0, 30, -9, 0);
        LightProbeTest.carve(world, 0, -10, 2, 30, -9, 2);
        world.setBlock(new BlockPos(4, -10, 0), BlockId.TORCH, 64);
        var light = lighting(world).update(FEET, EYE, 0);
        assertEquals(14, light.level(4, -10, 0));
        assertEquals(10, light.level(8, -10, 0));
        assertEquals(13, light.level(4, -9, 0));
        assertEquals(0, light.level(4, -10, 2));
        assertEquals(0, light.level(4, -11, 0));
        assertTrue(light.held().isEmpty());
    }

    @Test
    void editsRefreshTheFieldAfterAShortThrottle() {
        var world = LightProbeTest.groundBelowZero();
        LightProbeTest.carve(world, 0, -10, 0, 20, -10, 0);
        var lighting = lighting(world);
        assertEquals(0, lighting.update(FEET, EYE, 0).level(5, -10, 0));
        world.setBlock(new BlockPos(5, -10, 0), BlockId.TORCH, 64);
        assertEquals(0, lighting.update(FEET, EYE, 0).level(5, -10, 0));
        for (int i = 0; i < 6; i++) lighting.update(FEET, EYE, 0);
        assertEquals(14, lighting.update(FEET, EYE, 0).level(5, -10, 0));
        world.setBlock(new BlockPos(6, -10, 0), BlockId.STONE, 64);
        for (int i = 0; i < 7; i++) lighting.update(FEET, EYE, 0);
        assertEquals(0, lighting.update(FEET, EYE, 0).level(7, -10, 0));
        world.setBlock(new BlockPos(5, -10, 0), BlockId.AIR, 64);
        for (int i = 0; i < 7; i++) lighting.update(FEET, EYE, 0);
        assertEquals(0, lighting.update(FEET, EYE, 0).level(5, -10, 0));
    }

    @Test
    void unrelatedEditsDoNotTriggerAFlood() {
        var world = LightProbeTest.groundBelowZero();
        var lighting = lighting(world);
        var first = lighting.update(FEET, EYE, 0).placed();
        world.setBlock(new BlockPos(20, -20, 20), BlockId.AIR, 64);
        for (int i = 0; i < 10; i++)
            assertSame(first, lighting.update(FEET, EYE, 0).placed());
    }

    @Test
    void heldLightFollowsTheEyeAndDisappearsWhenPutAway() {
        var world = LightProbeTest.groundBelowZero();
        LightProbeTest.carve(world, 0, -10, 0, 30, -9, 0);
        var lighting = lighting(world);
        var eye = new BlockPos(10, -9, 0);
        var light = lighting.update(new BlockPos(10, -10, 0), eye, 14);
        assertEquals(14, light.level(10, -9, 0));
        assertEquals(10, light.level(14, -9, 0));
        assertEquals(0, light.level(10, -9, 2));
        var moved = lighting.update(new BlockPos(20, -10, 0), new BlockPos(20, -9, 0), 14);
        assertEquals(14, moved.level(20, -9, 0));
        assertEquals(0, moved.level(5, -9, 0));
        assertTrue(lighting.update(FEET, eye, 0).held().isEmpty());
    }

    @Test
    void theFieldRecentersWhenThePlayerTravels() {
        var world = LightProbeTest.groundBelowZero();
        var lighting = lighting(world);
        var near = lighting.update(FEET, EYE, 0).placed();
        assertTrue(near.originX() <= 0 && near.originX() + near.sizeX() > 16);
        for (int i = 0; i < 7; i++) lighting.update(new BlockPos(1000, -10, 8), EYE, 0);
        var far = lighting.update(new BlockPos(1000, -10, 8), EYE, 0).placed();
        assertTrue(far.originX() <= 1000 && far.originX() + far.sizeX() > 1000);
    }

    private static BlockLighting lighting(World world) {
        var lighting = new BlockLighting(world);
        world.setBlockChangeListener(lighting::onBlockChanged);
        return lighting;
    }
}
