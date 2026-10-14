package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.ui.screens.SettingsScreen;
import org.junit.jupiter.api.Test;

class ScreenManagerTest {
    @Test
    void replacingAndClosingScreensCancelsTheirInteractions() {
        ScreenManager manager = new ScreenManager();
        Probe first = new Probe(), second = new Probe();
        manager.beginFrame();
        assertFalse(manager.blocksGameplay());
        manager.open(first);
        manager.updatePointer(false, false, 0, 0);
        manager.open(second);
        assertEquals(2, first.cancellations);
        assertSame(second, manager.current());
        manager.updatePointer(false, false, 0, 0);
        assertEquals(1, first.pointers);
        assertEquals(1, second.pointers);
        manager.cancelInteraction();
        assertTrue(manager.isOpen());
        manager.close();
        assertEquals(3, second.cancellations);
        assertFalse(manager.isOpen());
        assertTrue(manager.blocksGameplay());
        manager.beginFrame();
        assertFalse(manager.blocksGameplay());
    }

    @Test
    void closeActionConsumesTheEntireFrameAndRemainingKeysAreDiscarded() {
        ScreenManager manager = new ScreenManager();
        Probe screen = new Probe();
        screen.action = Screen.Action.CLOSE;
        manager.open(screen);
        manager.beginFrame();
        manager.keyPress(UiKey.BACK);
        manager.keyPress(UiKey.ACTIVATE);
        assertEquals(1, screen.keys);
        assertFalse(manager.isOpen());
        assertTrue(manager.blocksGameplay());
        manager.render(new UiCanvas(), new UiViewport(640, 360, 640, 360));
        assertEquals(0, screen.renders);
        manager.beginFrame();
        assertFalse(manager.blocksGameplay());
    }

    @Test
    void openingAndReopeningRealSettingsRequiresReleaseAndResetsFocus() {
        ScreenManager manager = new ScreenManager();
        SettingsScreen screen = new SettingsScreen(new GameSettings());
        manager.open(screen);
        manager.updatePointer(true, true, SettingsScreen.SLIDER_X, SettingsScreen.SLIDER_Y);
        assertEquals(4, screen.renderDistance());
        manager.updatePointer(false, false, 0, 0);
        manager.updatePointer(true, true, SettingsScreen.SLIDER_X, SettingsScreen.SLIDER_Y);
        assertTrue(screen.slider().dragging());
        manager.close();
        manager.open(screen);
        assertFalse(screen.slider().dragging());
        assertFalse(screen.slider().focused());
        manager.keyPress(UiKey.BACK);
        manager.beginFrame();
        manager.keyPress(UiKey.INCREASE);
        assertEquals(GameSettings.MIN_RENDER_DISTANCE, screen.renderDistance());
        assertFalse(manager.blocksGameplay());
    }

    private static final class Probe implements Screen {
        int cancellations, pointers, keys, renders;
        Action action = Action.NONE;

        public void render(UiCanvas canvas, UiViewport viewport) {
            renders++;
        }

        public Action updatePointer(boolean down, boolean pressed, double x, double y) {
            pointers++;
            return action;
        }

        public Action keyPress(UiKey key) {
            keys++;
            return action;
        }

        public void cancelInteraction() {
            cancellations++;
        }
    }
}
