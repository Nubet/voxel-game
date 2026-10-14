package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.*;
import org.junit.jupiter.api.Test;

class SaveErrorScreenTest {
    @Test
    void escapeNeverDiscardsAndKeyboardRequiresExplicitChoice() {
        var screen = new SaveErrorScreen();
        screen.layout(new UiViewport(1280, 720, 1280, 720));
        assertEquals(Screen.Action.NONE, screen.keyPress(UiKey.BACK));
        assertEquals(Screen.Action.RETRY_SAVE, screen.keyPress(UiKey.ACTIVATE));
        screen.keyPress(UiKey.NEXT);
        assertEquals(Screen.Action.DISCARD_CHANGES, screen.keyPress(UiKey.ACTIVATE));
        screen.cancelInteraction();
        assertEquals(Screen.Action.RETRY_SAVE, screen.keyPress(UiKey.ACTIVATE));
    }

    @Test
    void heldMouseCannotDiscardOnOpeningOrResize() {
        var screen = new SaveErrorScreen();
        var viewport = new UiViewport(320, 240, 320, 240);
        screen.layout(viewport);
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, true, 100, 140));
        assertEquals(Screen.Action.NONE, screen.updatePointer(false, false, 100, 140));
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, true, 100, 140));
        assertEquals(Screen.Action.DISCARD_CHANGES, screen.updatePointer(false, false, 100, 140));
    }
}
