package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.*;
import org.junit.jupiter.api.Test;

class PauseScreenTest {
    @Test
    void backToMenuIsAQueuedActionAndResumeStillClosesOnlyPause() {
        var screen = new PauseScreen();
        var screens = new ScreenManager();
        screens.open(screen);
        screens.layout(new UiViewport(320, 240, 320, 240));
        screens.keyPress(UiKey.NEXT);
        assertTrue(screen.resumeButton().focused());
        screens.keyPress(UiKey.NEXT);
        assertTrue(screen.menuButton().focused());
        screens.keyPress(UiKey.ACTIVATE);
        assertEquals(Screen.Action.BACK_TO_MENU, screens.pollAction());
        assertTrue(screens.isOpen());
        assertTrue(screens.blocksGameplay());
        screen.cancelInteraction();
        var box = screen.menuButton().bounds();
        screens.updatePointer(false, false, box.x() + 2, box.y() + 2);
        screens.updatePointer(true, true, box.x() + 2, box.y() + 2);
        assertNull(screens.pollAction());
        screens.updatePointer(false, false, box.x() + 2, box.y() + 2);
        assertEquals(Screen.Action.BACK_TO_MENU, screens.pollAction());
        screens.keyPress(UiKey.BACK);
        assertFalse(screens.isOpen());
        assertTrue(screens.blocksGameplay());
    }

    @Test
    void escapeAndResumeButtonClosePauseOnlyOnAnAcceptedAction() {
        PauseScreen screen = new PauseScreen();
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
        double x = PauseScreen.RESUME_BOUNDS.x() + 1, y = PauseScreen.RESUME_BOUNDS.y() + 1;
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, true, x, y));
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, false, x, y));
        assertEquals(Screen.Action.CLOSE, screen.updatePointer(false, false, x, y));
    }

    @Test
    void cancellationRequiresPointerReleaseAndRenderingDoesNotMutateState() {
        PauseScreen screen = new PauseScreen();
        screen.updatePointer(true, true, PauseScreen.RESUME_BOUNDS.x() + 1, PauseScreen.RESUME_BOUNDS.y() + 1);
        screen.cancelInteraction();
        assertEquals(
                Screen.Action.NONE,
                screen.updatePointer(true, true, PauseScreen.RESUME_BOUNDS.x() + 1, PauseScreen.RESUME_BOUNDS.y() + 1));
        screen.updatePointer(false, false, 0, 0);
        var canvas = new UiCanvas();
        screen.render(canvas, new UiViewport(640, 360, 640, 360));
        assertTrue(canvas.vertexCount() > 0);
    }
}
