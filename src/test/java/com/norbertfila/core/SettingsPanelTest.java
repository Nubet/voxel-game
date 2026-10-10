package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsPanelTest {
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
