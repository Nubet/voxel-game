package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SliderTest {
    private static final UiBounds TRACK = new UiBounds(20, 30, 200, 16);

    @Test
    void capturesDragOutsideBothAxesAndCommitsReleasePosition() {
        Slider slider = new Slider(TRACK, 2, 16, 1, 4);
        assertTrue(slider.updatePointer(true, true, 20, 30));
        assertEquals(2, slider.value());
        assertTrue(slider.dragging());
        assertTrue(slider.updatePointer(true, false, 1000, -500));
        assertEquals(16, slider.value());
        assertTrue(slider.updatePointer(false, false, -100, 500));
        assertEquals(2, slider.value());
        assertFalse(slider.dragging());
        assertFalse(slider.updatePointer(true, false, 220, 30));
        assertEquals(2, slider.value());
    }

    @Test
    void paintedKnobBoundsAreClickableButEnteringWithHeldMouseCannotStartDrag() {
        Slider slider = new Slider(TRACK, 0, 100, 5, 50);
        slider.updatePointer(true, true, 0, 0);
        assertFalse(slider.updatePointer(true, false, 120, 30));
        assertFalse(slider.dragging());
        assertTrue(slider.updatePointer(true, true, 16, 26));
        assertEquals(0, slider.value());
        slider.cancelInteraction();
        assertFalse(slider.dragging());
        assertFalse(slider.updatePointer(true, false, 220, 30));
    }

    @Test
    void keyboardHonorsFocusStepAndLimitsIncludingFullIntRange() {
        Slider slider = new Slider(TRACK, -10, 10, 5, 0);
        assertFalse(slider.keyPress(UiKey.INCREASE));
        slider.setFocused(true);
        assertTrue(slider.keyPress(UiKey.INCREASE));
        assertEquals(5, slider.value());
        assertTrue(slider.keyPress(UiKey.INCREASE));
        assertFalse(slider.keyPress(UiKey.INCREASE));
        assertEquals(1, slider.fraction());
        Slider full = new Slider(TRACK, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
        full.setFocused(true);
        assertFalse(full.keyPress(UiKey.INCREASE));
        assertTrue(full.updatePointer(true, true, 20, 30));
        assertEquals(Integer.MIN_VALUE, full.value());
    }

    @Test
    void rejectsInvalidRangeStepAndValueAndCancelsNonFiniteDrag() {
        assertThrows(IllegalArgumentException.class, () -> new Slider(TRACK, 2, 2, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Slider(TRACK, 0, 10, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new Slider(TRACK, 0, 10, 3, 0));
        Slider slider = new Slider(TRACK, 0, 10, 2, 4);
        assertThrows(IllegalArgumentException.class, () -> slider.setValue(3));
        assertThrows(IllegalArgumentException.class, () -> slider.setValue(12));
        slider.updatePointer(true, true, 20, 30);
        assertFalse(slider.updatePointer(true, false, Double.NaN, 30));
        assertFalse(slider.dragging());
        assertEquals(0, slider.value());
    }
}
