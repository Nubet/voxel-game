package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SettingsPanelTest {
    @ParameterizedTest
    @CsvSource({"1280,720,1280,720", "640,360,1280,720", "1280,720,1920,1080", "640,480,1280,720", "853,601,1281,903"})
    void sliderHitTestingUsesTheSameCoordinatesAsRendering(
            int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
        UiViewport viewport = new UiViewport(windowWidth, windowHeight, framebufferWidth, framebufferHeight);
        SettingsPanel panel = new SettingsPanel(new GameSettings());
        panel.toggle();
        double windowY = (SettingsPanel.SLIDER_Y + 1) * viewport.scale() * (double) windowHeight / framebufferHeight;
        for (int x : new int[] {SettingsPanel.SLIDER_X, SettingsPanel.SLIDER_X + SettingsPanel.SLIDER_WIDTH}) {
            double insideX = x == SettingsPanel.SLIDER_X ? x + .25 : x - .25;
            double windowX = insideX * viewport.scale() * windowWidth / framebufferWidth;
            assertTrue(panel.update(true, true, viewport.cursorX(windowX), viewport.cursorY(windowY)));
            assertEquals(
                    x == SettingsPanel.SLIDER_X ? GameSettings.MIN_RENDER_DISTANCE : GameSettings.MAX_RENDER_DISTANCE,
                    panel.renderDistance());
        }
        double outsideX = (SettingsPanel.SLIDER_X - 7) * viewport.scale() * (double) windowWidth / framebufferWidth;
        assertFalse(panel.update(true, true, viewport.cursorX(outsideX), viewport.cursorY(windowY)));
    }

    @Test
    void sliderMapsLeftAndRightEdgesToConfiguredRange() {
        SettingsPanel panel = new SettingsPanel(new GameSettings());
        panel.toggle();

        assertTrue(panel.update(true, true, SettingsPanel.SLIDER_X, SettingsPanel.SLIDER_Y));
        assertEquals(GameSettings.MIN_RENDER_DISTANCE, panel.renderDistance());
        assertTrue(
                panel.update(true, true, SettingsPanel.SLIDER_X + SettingsPanel.SLIDER_WIDTH, SettingsPanel.SLIDER_Y));
        assertEquals(GameSettings.MAX_RENDER_DISTANCE, panel.renderDistance());
    }

    @Test
    void closedPanelDoesNotConsumeMouseChanges() {
        SettingsPanel panel = new SettingsPanel(new GameSettings());

        assertFalse(panel.update(true, true, SettingsPanel.SLIDER_X, SettingsPanel.SLIDER_Y));
        assertEquals(4, panel.renderDistance());
    }

    @Test
    void buttonsChangeDistanceByOneOnlyOnReleaseInside() {
        GameSettings settings = new GameSettings();
        SettingsPanel panel = new SettingsPanel(settings);
        panel.toggle();

        assertFalse(panel.update(true, true, SettingsPanel.PLUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertEquals(4, panel.renderDistance());
        assertFalse(panel.update(true, false, SettingsPanel.PLUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertTrue(panel.update(false, false, SettingsPanel.PLUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertEquals(5, panel.renderDistance());
        assertFalse(panel.update(false, false, SettingsPanel.PLUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertFalse(panel.update(true, true, SettingsPanel.MINUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertTrue(panel.update(false, false, SettingsPanel.MINUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertEquals(4, panel.renderDistance());
    }

    @Test
    void boundaryButtonsDoNotThrowOrChangeValue() {
        GameSettings settings = new GameSettings();
        SettingsPanel panel = new SettingsPanel(settings);
        panel.toggle();

        settings.setRenderDistance(GameSettings.MIN_RENDER_DISTANCE);
        assertFalse(panel.update(true, true, SettingsPanel.MINUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertEquals(GameSettings.MIN_RENDER_DISTANCE, panel.renderDistance());
        settings.setRenderDistance(GameSettings.MAX_RENDER_DISTANCE);
        assertFalse(panel.update(true, true, SettingsPanel.PLUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertEquals(GameSettings.MAX_RENDER_DISTANCE, panel.renderDistance());
    }

    @Test
    void tabCyclesEnabledControlsAndKeyboardEditsValidatedSettings() {
        GameSettings settings = new GameSettings();
        settings.setRenderDistance(GameSettings.MIN_RENDER_DISTANCE);
        SettingsPanel panel = new SettingsPanel(settings);
        panel.toggle();
        panel.keyPress(UiKey.NEXT);
        assertTrue(panel.slider().focused());
        assertTrue(panel.keyPress(UiKey.INCREASE));
        assertEquals(3, settings.renderDistance());
        panel.keyPress(UiKey.NEXT);
        assertTrue(panel.increaseButton().focused());
        assertTrue(panel.keyPress(UiKey.ACTIVATE));
        assertEquals(4, settings.renderDistance());
        panel.keyPress(UiKey.PREVIOUS);
        assertTrue(panel.slider().focused());
        panel.keyPress(UiKey.PREVIOUS);
        assertTrue(panel.decreaseButton().focused());
        assertTrue(panel.keyPress(UiKey.ACTIVATE));
        assertTrue(panel.keyPress(UiKey.ACTIVATE));
        assertEquals(2, settings.renderDistance());
        assertFalse(panel.decreaseButton().focused());
        assertFalse(panel.keyPress(UiKey.ACTIVATE));
        panel.keyPress(UiKey.PREVIOUS);
        assertTrue(panel.increaseButton().focused());
        settings.setRenderDistance(GameSettings.MAX_RENDER_DISTANCE);
        panel.keyPress(UiKey.NEXT);
        assertTrue(panel.decreaseButton().focused());
        panel.keyPress(UiKey.PREVIOUS);
        assertTrue(panel.slider().focused());
    }

    @Test
    void cancellationRequiresPointerReleaseAndCloseDiscardsPendingClicks() {
        SettingsPanel panel = new SettingsPanel(new GameSettings());
        panel.toggle();
        panel.update(true, true, SettingsPanel.SLIDER_X, SettingsPanel.SLIDER_Y);
        assertTrue(panel.slider().dragging());
        panel.cancelInteraction();
        assertFalse(panel.slider().dragging());
        assertFalse(panel.update(true, true, 1000, -100));
        panel.update(false, false, 1000, -100);
        assertTrue(
                panel.update(true, true, SettingsPanel.SLIDER_X + SettingsPanel.SLIDER_WIDTH, SettingsPanel.SLIDER_Y));
        assertEquals(GameSettings.MAX_RENDER_DISTANCE, panel.renderDistance());
        panel.update(false, false, SettingsPanel.SLIDER_X + SettingsPanel.SLIDER_WIDTH, SettingsPanel.SLIDER_Y);
        panel.update(true, true, SettingsPanel.MINUS_X + 1, SettingsPanel.BUTTON_Y + 1);
        panel.toggle();
        panel.toggle();
        assertFalse(panel.update(false, false, SettingsPanel.MINUS_X + 1, SettingsPanel.BUTTON_Y + 1));
        assertEquals(GameSettings.MAX_RENDER_DISTANCE, panel.renderDistance());
        panel.toggle();
        assertFalse(panel.keyPress(UiKey.ACTIVATE));
    }
}
