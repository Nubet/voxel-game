package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.*;
import org.junit.jupiter.api.Test;

class MainMenuScreenTest {
    @Test
    void navigationAndPointerReturnTypedSessionActionsWithoutClosingTheMenu() {
        var menu = new MainMenuScreen(false);
        var screens = new ScreenManager();
        screens.open(menu);
        screens.layout(new UiViewport(1920, 1080, 1920, 1080));
        assertEquals(Screen.Action.PLAY, menu.keyPress(UiKey.ACTIVATE));
        menu.keyPress(UiKey.NEXT);
        assertEquals(Screen.Action.QUIT, menu.keyPress(UiKey.ACTIVATE));
        menu.keyPress(UiKey.PREVIOUS);
        assertTrue(menu.playButton().focused());
        assertEquals(Screen.Action.QUIT, menu.keyPress(UiKey.BACK));
        var box = menu.playButton().bounds();
        screens.updatePointer(false, false, box.x() + 2, box.y() + 2);
        screens.updatePointer(true, true, box.x() + 2, box.y() + 2);
        assertNull(screens.pollAction());
        screens.updatePointer(false, false, box.x() + 2, box.y() + 2);
        assertEquals(Screen.Action.PLAY, screens.pollAction());
        assertTrue(screens.isOpen());
        assertTrue(screens.blocksGameplay());
    }

    @Test
    void menuFitsTheMinimumGuiAndResizeCancelsClicks() {
        var menu = new MainMenuScreen(true);
        var viewport = new UiViewport(320, 240, 320, 240);
        menu.layout(viewport);
        var play = menu.playButton().bounds();
        menu.updatePointer(false, false, 0, 0);
        menu.updatePointer(true, true, play.x() + 2, play.y() + 2);
        menu.layout(new UiViewport(640, 360, 640, 360));
        var moved = menu.playButton().bounds();
        assertEquals(Screen.Action.NONE, menu.updatePointer(false, false, moved.x() + 2, moved.y() + 2));
        var canvas = new UiCanvas();
        menu.render(canvas, viewport);
        assertTrue(canvas.vertexCount() > 0);
        assertTrue(menu.playButton().bounds().x() >= 0);
        assertTrue(menu.quitButton().bounds().y() + 80 < viewport.height());
    }

    @Test
    void savingScreenConsumesAllActions() {
        var screen = new SessionStatusScreen("Saving world...");
        for (UiKey key : UiKey.values()) assertEquals(Screen.Action.NONE, screen.keyPress(key));
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, true, 10, 10));
        var canvas = new UiCanvas();
        screen.render(canvas, new UiViewport(320, 240, 320, 240));
        assertTrue(canvas.vertexCount() > 0);
    }
}
