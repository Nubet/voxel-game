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
}
