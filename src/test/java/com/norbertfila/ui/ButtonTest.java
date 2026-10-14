package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ButtonTest {
    @Test
    void activatesOnceOnReleaseInsideAndTracksHoverAndPress() {
        Button button = new Button(new UiBounds(10, 20, 80, 24), "Resume");
        assertFalse(button.updatePointer(true, true, 20, 25));
        assertTrue(button.hovered());
        assertTrue(button.pressed());
        assertFalse(button.updatePointer(true, false, 20, 25));
        assertTrue(button.updatePointer(false, false, 20, 25));
        assertFalse(button.pressed());
        assertFalse(button.updatePointer(false, false, 20, 25));
    }

    @Test
    void releaseOutsidePressOutsideAndCancellationDoNotActivate() {
        Button button = new Button(new UiBounds(10, 20, 80, 24), "Resume");
        button.updatePointer(true, true, 20, 25);
        assertFalse(button.updatePointer(false, false, 90, 25));
        button.updatePointer(true, true, 0, 0);
        button.updatePointer(true, false, 20, 25);
        assertFalse(button.updatePointer(false, false, 20, 25));
        button.updatePointer(true, true, 20, 25);
        button.cancelInteraction();
        assertFalse(button.updatePointer(false, false, 20, 25));
    }

    @Test
    void disabledOrUnfocusedControlsRejectKeyboardAndDisablingCancelsClick() {
        Button button = new Button(new UiBounds(10, 20, 80, 24), "Resume");
        assertFalse(button.keyPress(UiKey.ACTIVATE));
        button.setFocused(true);
        assertFalse(button.keyPress(UiKey.INCREASE));
        assertTrue(button.keyPress(UiKey.ACTIVATE));
        assertTrue(button.pressed());
        button.updatePointer(true, true, 20, 25);
        button.setEnabled(false);
        assertFalse(button.pressed());
        assertFalse(button.focused());
        assertFalse(button.keyPress(UiKey.ACTIVATE));
        assertFalse(button.updatePointer(false, false, 20, 25));
        button.setEnabled(true);
        assertFalse(button.updatePointer(false, false, 20, 25));
    }
}
