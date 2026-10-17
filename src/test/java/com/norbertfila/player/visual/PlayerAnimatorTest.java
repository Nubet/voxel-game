package com.norbertfila.player.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.*;
import com.norbertfila.player.visual.model.PlayerModel;
import com.norbertfila.player.visual.model.PlayerModel.Part;
import com.norbertfila.player.visual.model.PlayerPose;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerAnimatorTest {
    private static final double DT = 1.0 / 60;
    private static final WaterContact.Result DRY = new WaterContact.Result(0, false, true);
    private static final PlayerInput WALK = new PlayerInput(true, false, false, false, false, false, false);
    private static final PlayerInput RUN = new PlayerInput(true, false, false, false, false, false, true);
    private final World world = flat();
    private final PlayerController player = new PlayerController();
    private final PlayerRenderStateExtractor extractor = new PlayerRenderStateExtractor();

    PlayerAnimatorTest() {
        player.setPosition(new PlayerState.Position(8, 1, 8));
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        extract(0, false);
    }

    @Test
    void resolvedDistanceDrivesOpposingWalkAndRunButNotHeldKeysAgainstAWall() {
        PlayerRenderState walk = null;
        for (int i = 0; i < 12; i++) walk = step(WALK);
        var pose = PlayerAnimator.pose(walk, 1);
        float leg = pose.transform(Part.RIGHT_LEG).pitch();
        assertNotEquals(0, leg);
        assertEquals(-leg, pose.transform(Part.LEFT_LEG).pitch());
        assertEquals(-leg, pose.transform(Part.RIGHT_ARM).pitch());
        assertEquals(leg, pose.transform(Part.LEFT_ARM).pitch());
        double walkIntensity = walk.animation().current().intensity();
        PlayerRenderState run = null;
        for (int i = 0; i < 10; i++) run = step(RUN);
        assertTrue(run.animation().current().intensity() > walkIntensity);
        assertTrue(
                run.animation().current().phase() > walk.animation().current().phase());
        var chunk = world.getChunk(new ChunkPos(0, 0, 0));
        for (int y = 1; y <= 3; y++) for (int z = 0; z < 16; z++) chunk.set(12, y, z, BlockId.STONE);
        PlayerRenderState wall = null;
        for (int i = 0; i < 90; i++) wall = step(RUN);
        assertTrue(wall.sprinting());
        assertEquals(0, wall.animation().previous().phase());
        assertEquals(0, wall.animation().current().intensity());
        assertEquals(0, PlayerAnimator.pose(wall, .5).transform(Part.RIGHT_LEG).pitch());
    }

    @Test
    void forcedCrouchBendsAtTorsoPivotOffsetsHipsAndKeepsHeadLevelAfterKeyRelease() {
        world.getChunk(new ChunkPos(0, 0, 0)).set(8, 3, 8, BlockId.STONE);
        player.tick(world, new PlayerInput(false, false, false, false, true, true, false), DT, 0, 0);
        for (int i = 0; i < 2; i++)
            player.tick(world, new PlayerInput(false, false, false, false, false, true, false), DT, 0, 0);
        var state = step(PlayerInput.NONE);
        assertTrue(state.crouching());
        var pose = PlayerAnimator.pose(state, 1);
        assertEquals(-.5f, pose.transform(Part.BODY).pitch());
        assertEquals(.5f, pose.transform(Part.HEAD).pitch());
        assertEquals(2, pose.transform(Part.RIGHT_LEG).z());
        var torso = PlayerModel.partMatrix(Part.BODY, pose, new Matrix4f());
        var pivot = torso.transformPosition(new Vector3f());
        assertEquals(10f / 16, pivot.y, 1e-6);
        assertEquals(2f / 16, pivot.z, 1e-6);
        var head = PlayerModel.partMatrix(Part.HEAD, pose, new Matrix4f());
        assertTrue(head.transformPosition(new Vector3f()).z < 0);
        assertEquals(0, head.transformDirection(new Vector3f(0, 0, -1)).y, 1e-6);
    }

    @Test
    void jumpFallCreativeFlightAndSpectatorUsePublishedMotionInsteadOfJumpKeys() {
        var jump = step(new PlayerInput(false, false, false, false, true, false, false));
        assertTrue(jump.velocityY() > 0);
        var rising = PlayerAnimator.pose(jump, 1);
        PlayerRenderState fall;
        do {
            fall = step(PlayerInput.NONE);
        } while (fall.velocityY() >= 0);
        var falling = PlayerAnimator.pose(fall, 1);
        assertNotEquals(rising.transform(Part.RIGHT_ARM), falling.transform(Part.RIGHT_ARM));
        player.restore(new PlayerState.Position(8, 5, 8), PlayerController.Mode.CREATIVE, true);
        var flight = extract(0, false);
        assertTrue(flight.flying());
        assertNotEquals(
                falling.transform(Part.RIGHT_ARM),
                PlayerAnimator.pose(flight, 1).transform(Part.RIGHT_ARM));
        player.restore(new PlayerState.Position(8, 5, 8), PlayerController.Mode.SPECTATOR, false);
        assertEquals(
                PlayerAnimator.pose(flight, 1).transform(Part.RIGHT_ARM).pitch(),
                PlayerAnimator.pose(extract(0, false), 1)
                        .transform(Part.RIGHT_ARM)
                        .pitch());
    }

    @Test
    void shortestYawSeamBoundedHeadAndFrameTimeLookDoNotMutatePublishedPose() {
        player.pauseInterpolation();
        extract(179, false);
        var state = extract(-179, false);
        assertEquals(180, state.interpolate(.5).yaw());
        var pose = PlayerAnimator.pose(state, .5);
        assertTrue(Math.abs(pose.transform(Part.HEAD).yaw()) < .04);
        var look = PlayerAnimator.pose(state, .5, 20, 35);
        assertEquals(Math.toRadians(60), Math.abs(look.transform(Part.HEAD).yaw()), 1e-6);
        assertEquals(Math.toRadians(35), look.transform(Part.HEAD).pitch(), 1e-6);
        assertEquals(pose, PlayerAnimator.pose(state, .5));
        assertThrows(UnsupportedOperationException.class, () -> pose.parts().clear());
        assertThrows(IllegalArgumentException.class, () -> PlayerAnimator.pose(state, Double.NaN));
        var turned = extract(20, false);
        double bodyGap = ((20 - turned.animation().current().bodyYaw() + 540) % 360) - 180;
        assertTrue(Math.abs(bodyGap) <= 60);
        assertEquals(0, turned.animation().current().intensity());
    }

    @Test
    void teleportPauseBlockedAndStallDiscardGaitAndInterpolationWithoutAdvancingFrozenTime() {
        for (int i = 0; i < 8; i++) step(WALK);
        player.setPosition(new PlayerState.Position(3, 1, 3));
        var teleport = extract(90, false);
        assertEquals(teleport.animation().previous(), teleport.animation().current());
        assertEquals(0, teleport.animation().current().phase());
        for (int i = 0; i < 3; i++) {
            step(WALK);
            player.pauseInterpolation();
            var frozen = extract(130, true);
            assertEquals(frozen.animation().previous(), frozen.animation().current());
            assertEquals(0, frozen.animation().current().intensity());
            assertEquals(frozen.animation(), extract(130, true).animation());
        }
    }

    @Test
    void swingSequenceRetainsMissedClicksAcrossSkippedFramesAndMiningRepeatsOnTickClock() {
        // Accepted clicks restart even in air; held valid mining repeats every 12 ticks, independently of breaks.
        extractor.swing(10, true);
        var start = extractor.extract(player, 0, 0, DRY, false, PlayerAppearance.DEFAULT, 10);
        var visible = extractor.extract(player, 0, 0, DRY, false, PlayerAppearance.DEFAULT, 16);
        assertEquals(new PlayerAnimator.Swing(1, 10), visible.animation().swing());
        assertTrue(PlayerAnimator.pose(visible, 1).transform(Part.RIGHT_ARM).pitch()
                > PlayerAnimator.pose(start, 1).transform(Part.RIGHT_ARM).pitch() + 1);
        extractor.swing(17, false);
        var unchanged = extractor.extract(player, 0, 0, DRY, false, PlayerAppearance.DEFAULT, 17);
        assertEquals(visible.animation().swing(), unchanged.animation().swing());
        extractor.swing(22, false);
        var repeated = extractor.extract(player, 0, 0, DRY, false, PlayerAppearance.DEFAULT, 28);
        assertEquals(new PlayerAnimator.Swing(2, 22), repeated.animation().swing());
        extractor.swing(29, true);
        var clicked = extractor.extract(player, 0, 0, DRY, false, PlayerAppearance.DEFAULT, 29);
        assertEquals(new PlayerAnimator.Swing(3, 29), clicked.animation().swing());
        var expired = extractor.extract(player, 0, 0, DRY, false, PlayerAppearance.DEFAULT, 50);
        assertEquals(new PlayerAnimator.Swing(3, 29), expired.animation().swing());
        assertEquals(
                0, PlayerAnimator.pose(expired, 1).transform(Part.RIGHT_ARM).pitch(), 1e-6);
    }

    @Test
    void fixedTickGaitAndRenderSamplingAreIndependentOfFps() {
        var expected = script(60);
        for (int fps : new int[] {30, 144, 256}) assertEquals(expected, script(fps));
    }

    private static PlayerPose script(int fps) {
        var fixture = new PlayerAnimatorTest();
        var clock = new FixedSimulation(DT, 8);
        int[] ticks = {0};
        PlayerRenderState[] state = {fixture.extract(0, false)};
        while (ticks[0] < 45) {
            clock.advance(1.0 / fps, dt -> {
                if (ticks[0]++ < 45) state[0] = fixture.step(RUN);
            });
            PlayerAnimator.pose(state[0], .25);
            PlayerAnimator.pose(state[0], .75);
        }
        return PlayerAnimator.pose(state[0], .5);
    }

    private PlayerRenderState step(PlayerInput input) {
        player.tick(world, input, DT, 0, 0);
        return extract(0, false);
    }

    private PlayerRenderState extract(double yaw, boolean frozen) {
        return extractor.extract(player, yaw, 0, DRY, frozen);
    }

    private static World flat() {
        var world = new World();
        for (int cx = -1; cx <= 1; cx++)
            for (int cz = -1; cz <= 1; cz++) {
                var chunk = new Chunk();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) chunk.set(x, 0, z, BlockId.STONE);
                world.putChunk(new ChunkPos(cx, 0, cz), chunk);
            }
        return world;
    }
}
