package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        double outsideX = (SettingsPanel.SLIDER_X - 1) * viewport.scale() * (double) windowWidth / framebufferWidth;
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
    void buttonsChangeDistanceByOneOnlyOnPress() {
        GameSettings settings = new GameSettings();
        SettingsPanel panel = new SettingsPanel(settings);
        panel.toggle();

        assertTrue(panel.update(true, true, SettingsPanel.PLUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertEquals(5, panel.renderDistance());
        assertFalse(panel.update(true, false, SettingsPanel.PLUS_X + 1, SettingsPanel.BUTTON_Y + 10));
        assertTrue(panel.update(true, true, SettingsPanel.MINUS_X + 1, SettingsPanel.BUTTON_Y + 10));
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
}
