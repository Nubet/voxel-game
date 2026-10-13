package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.PlayerState;
import com.norbertfila.player.WaterContact;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class UnderwaterVisibilityTest {
    @Test
    void dryViewIsExactAndEntryExitAreSmoothAndBounded() {
        var visibility = new UnderwaterVisibility();
        visibility.update(false, true, 1);
        assertEquals(0, visibility.amount());
        assertEquals(.55f, visibility.backgroundRed());
        assertEquals(.76f, visibility.backgroundGreen());
        assertEquals(.96f, visibility.backgroundBlue());
        visibility.update(true, true, 1d / 60);
        assertTrue(visibility.amount() > 0 && visibility.amount() < .3);
        float entering = visibility.amount();
        visibility.update(true, true, .2);
        assertTrue(visibility.amount() > .9 && visibility.amount() < 1);
        visibility.update(true, true, 1);
        assertEquals(1, visibility.amount());
        assertEquals(UnderwaterVisibility.FOG_RED, visibility.backgroundRed(), 1e-7);
        assertEquals(UnderwaterVisibility.FOG_GREEN, visibility.backgroundGreen(), 1e-7);
        assertEquals(UnderwaterVisibility.FOG_BLUE, visibility.backgroundBlue(), 1e-7);
        visibility.update(false, true, 1d / 60);
        assertTrue(visibility.amount() < 1 && visibility.amount() > entering);
        visibility.update(false, true, 1);
        assertEquals(0, visibility.amount());
    }

    @Test
    void transitionIsIndependentOfFrameRateAndUnknownDataRetainsLastTarget() {
        var sixty = new UnderwaterVisibility();
        var faster = new UnderwaterVisibility();
        for (int i = 0; i < 6; i++) sixty.update(true, true, 1d / 60);
        for (int i = 0; i < 24; i++) faster.update(true, true, 1d / 240);
        assertEquals(sixty.amount(), faster.amount(), 1e-7);
        sixty.update(false, false, 1);
        assertEquals(1, sixty.amount());
        sixty.update(false, true, 1);
        sixty.update(true, false, 1);
        assertEquals(0, sixty.amount());
    }

    @Test
    void bodyImmersionDoesNotTriggerFogUntilEyesCrossActualSurface() {
        World world = new World();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.putChunk(new ChunkPos(x, 0, z), new Chunk());
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, 64);
        var player = PlayerState.at(new PlayerState.Position(.5, 1, .5));
        var standing = WaterContact.sample(world, player);
        assertTrue(standing.available() && standing.submergedFraction() > 0);
        var visibility = new UnderwaterVisibility();
        visibility.update(standing.eyesSubmerged(), standing.available(), 1);
        assertEquals(0, visibility.amount());
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) world.setBlock(new BlockPos(x, 2, z), BlockId.WATER, 64);
        var immersed = WaterContact.sample(world, player);
        visibility.update(immersed.eyesSubmerged(), immersed.available(), 1);
        assertEquals(1, visibility.amount());
    }

    @Test
    void invalidDeltaDoesNotChangePresentationState() {
        var visibility = new UnderwaterVisibility();
        for (double delta : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> visibility.update(true, true, delta));
        assertEquals(0, visibility.amount());
        visibility.update(true, true, 0);
        assertEquals(0, visibility.amount());
    }

    @Test
    void repeatedSurfaceCrossingsDoNotFlashBetweenFullyDryAndFullyWet() {
        var visibility = new UnderwaterVisibility();
        visibility.update(true, true, .08);
        float previous = visibility.amount();
        for (int i = 0; i < 60; i++) {
            visibility.update(i % 2 == 0, true, 1d / 60);
            assertTrue(visibility.amount() > .3 && visibility.amount() < .75);
            assertTrue(Math.abs(visibility.amount() - previous) < .15);
            previous = visibility.amount();
        }
    }
}
