package com.norbertfila.player.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class HandSwayTest {
    private static PlayerRenderState still() {
        var frame = new PlayerAnimator.Frame(0, 0, 0, 0, 0, 0, 0, 0);
        var sample = new PlayerRenderState.Sample(new PlayerState.Position(0, 0, 0), 1.62, 0, 0);
        return new PlayerRenderState(
                PlayerAppearance.DEFAULT,
                sample,
                sample,
                0,
                0,
                0,
                true,
                false,
                false,
                false,
                PlayerController.Mode.SURVIVAL,
                0,
                false,
                true,
                false,
                new PlayerAnimator.Animation(frame, frame, PlayerAnimator.Swing.NONE));
    }

    @Test
    void theHandTrailsATurnAndSettlesBackToRest() {
        var sway = new HandSway();
        sway.update(0, 0, 0);
        assertTrue(sway.transform(still(), 1, 0, 0).equals(new org.joml.Matrix4f(), 1e-6f));
        sway.update(90, 0, .05);
        var forward = sway.transform(still(), 1, 90, 0).transformDirection(new Vector3f(0, 0, -1));
        assertTrue(forward.x < -.01, "Hand should lag to the left after turning right: " + forward);
        for (int frame = 0; frame < 60; frame++) sway.update(90, 0, .05);
        assertTrue(sway.transform(still(), 1, 90, 0).equals(new org.joml.Matrix4f(), 1e-4f));
    }
}
