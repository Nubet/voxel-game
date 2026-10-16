package com.norbertfila.ui.crosshair;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CrosshairTransitionTest {
    @Test
    void usesElapsedTimeRatherThanFrameCount() {
        for (int fps : new int[] {30, 60, 144}) {
            var transition = new CrosshairTransition();
            int frames = (int) Math.floor(.04 * fps);
            for (int i = 0; i < frames; i++) transition.update(CrosshairStyle.BLOCK, true, 1.0 / fps);
            double elapsed = (double) frames / fps;
            transition.update(CrosshairStyle.BLOCK, true, .04 - elapsed);
            assertEquals(.5f, transition.visibility(), .00001);
            transition.update(CrosshairStyle.BLOCK, true, .04);
            assertEquals(1, transition.visibility());
            transition.update(CrosshairStyle.HIDDEN, true, .08);
            assertEquals(0, transition.visibility());
            assertEquals(CrosshairStyle.HIDDEN, transition.style());
        }
    }

    @Test
    void reversesFadeAndSwitchesStyleImmediatelyButUiResetNeverFades() {
        var transition = new CrosshairTransition();
        transition.update(CrosshairStyle.BLOCK, false, 0);
        transition.update(CrosshairStyle.HIDDEN, true, .02);
        assertEquals(.75f, transition.visibility());
        transition.update(CrosshairStyle.DEFAULT, true, .01);
        assertEquals(CrosshairStyle.DEFAULT, transition.style());
        assertEquals(.875f, transition.visibility());
        transition.reset();
        assertEquals(CrosshairStyle.HIDDEN, transition.style());
        assertEquals(0, transition.visibility());
        transition.update(CrosshairStyle.BLOCK, true, 10);
        assertEquals(1, transition.visibility());
        transition.update(CrosshairStyle.HIDDEN, false, 0);
        assertEquals(0, transition.visibility());
        assertThrows(IllegalArgumentException.class, () -> transition.update(CrosshairStyle.BLOCK, true, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> transition.update(CrosshairStyle.BLOCK, true, -1));
    }

    @Test
    void crossfadesStylesWithoutJumpingWhenInterrupted() {
        var transition = new CrosshairTransition();
        transition.update(CrosshairStyle.DEFAULT, false, 0);
        transition.update(CrosshairStyle.BLOCK, true, .04);
        assertEquals(.5f, transition.visibility(CrosshairStyle.DEFAULT), .00001);
        assertEquals(.5f, transition.visibility(CrosshairStyle.BLOCK), .00001);
        transition.update(CrosshairStyle.ENTITY, true, 0);
        assertEquals(.5f, transition.visibility(CrosshairStyle.DEFAULT), .00001);
        assertEquals(.5f, transition.visibility(CrosshairStyle.BLOCK), .00001);
        transition.update(CrosshairStyle.ENTITY, true, .04);
        assertEquals(.25f, transition.visibility(CrosshairStyle.DEFAULT), .00001);
        assertEquals(.25f, transition.visibility(CrosshairStyle.BLOCK), .00001);
        assertEquals(.5f, transition.visibility(CrosshairStyle.ENTITY), .00001);
        transition.update(CrosshairStyle.ENTITY, true, .04);
        assertEquals(0, transition.visibility(CrosshairStyle.DEFAULT));
        assertEquals(0, transition.visibility(CrosshairStyle.BLOCK));
        assertEquals(1, transition.visibility(CrosshairStyle.ENTITY));
        transition.reset();
        for (var style : CrosshairStyle.values()) assertEquals(0, transition.visibility(style));
    }

    @Test
    void styleBlendingUsesElapsedTimeAndCanBeDisabledMidTransition() {
        for (int fps : new int[] {30, 60, 144}) {
            var transition = new CrosshairTransition();
            transition.update(CrosshairStyle.DEFAULT, false, 0);
            int frames = (int) Math.floor(.02 * fps);
            for (int i = 0; i < frames; i++) transition.update(CrosshairStyle.BLOCK, true, 1.0 / fps);
            transition.update(CrosshairStyle.BLOCK, true, .02 - (double) frames / fps);
            assertEquals(.15625f, transition.visibility(CrosshairStyle.BLOCK), .00001);
            assertEquals(.84375f, transition.visibility(CrosshairStyle.DEFAULT), .00001);
            transition.update(CrosshairStyle.BLOCK, false, 0);
            assertEquals(1, transition.visibility(CrosshairStyle.BLOCK));
            assertEquals(0, transition.visibility(CrosshairStyle.DEFAULT));
        }
    }
}
