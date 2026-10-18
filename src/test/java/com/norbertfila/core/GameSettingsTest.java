package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameSettingsTest {
    @Test
    void changesRenderDistanceOnlyWhenValueChanges() {
        GameSettings settings = new GameSettings();

        assertEquals(12, settings.renderDistance());
        assertEquals(12, settings.simulationDistance());
        assertFalse(settings.setRenderDistance(12));
        assertTrue(settings.setRenderDistance(16));
        assertEquals(16, settings.renderDistance());
    }

    @Test
    void rejectsDistanceOutsideGuiRange() {
        GameSettings settings = new GameSettings();

        assertThrows(IllegalArgumentException.class, () -> settings.setRenderDistance(1));
        assertThrows(
                IllegalArgumentException.class, () -> settings.setRenderDistance(GameSettings.MAX_RENDER_DISTANCE + 1));
        assertTrue(settings.setRenderDistance(32));
    }

    @Test
    void keepsSimulationDistanceWithinRenderDistance() {
        GameSettings settings = new GameSettings();
        settings.setRenderDistance(12);

        assertTrue(settings.setSimulationDistance(6));
        assertThrows(IllegalArgumentException.class, () -> settings.setSimulationDistance(13));
        assertThrows(IllegalArgumentException.class, () -> settings.setSimulationDistance(0));
        settings.setRenderDistance(4);
        assertEquals(4, settings.simulationDistance());
        settings.setRenderDistance(16);
        assertEquals(4, settings.simulationDistance());
    }

    @Test
    void exposesValidatedCameraDefaultsAndZoomConfiguration() {
        GameSettings settings = new GameSettings();

        assertEquals(75f, settings.fieldOfView());
        assertEquals(20f, settings.zoomFieldOfView());
        assertEquals(.11f, settings.mouseSensitivity());
        assertEquals(12f, settings.zoomSpeed());
        assertTrue(settings.setFieldOfView(80));
        assertTrue(settings.setZoomFieldOfView(25));
        assertEquals(80f, settings.fieldOfView());
        assertEquals(25f, settings.zoomFieldOfView());
    }

    @Test
    void rejectsInvalidCameraSettings() {
        GameSettings settings = new GameSettings();

        assertThrows(IllegalArgumentException.class, () -> settings.setFieldOfView(0));
        assertThrows(IllegalArgumentException.class, () -> settings.setFieldOfView(111));
        assertThrows(IllegalArgumentException.class, () -> settings.setZoomFieldOfView(2));
        assertThrows(IllegalArgumentException.class, () -> settings.setMouseSensitivity(0));
        assertThrows(IllegalArgumentException.class, () -> settings.setZoomSpeed(Float.NaN));
    }

    @Test
    void validatesAndChangesGuiScale() {
        GameSettings settings = new GameSettings();

        assertEquals(GameSettings.GUI_SCALE_AUTO, settings.guiScale());
        assertTrue(settings.setGuiScale(4));
        assertEquals(4, settings.guiScale());
        assertTrue(settings.setGuiScale(GameSettings.GUI_SCALE_AUTO));
        assertFalse(settings.setGuiScale(GameSettings.GUI_SCALE_AUTO));
        assertThrows(IllegalArgumentException.class, () -> settings.setGuiScale(-1));
        assertThrows(IllegalArgumentException.class, () -> settings.setGuiScale(5));
    }

    @Test
    void validatesFrameRateLimitAndSupportsUnlimited() {
        GameSettings settings = new GameSettings();

        assertEquals(GameSettings.DEFAULT_FRAME_RATE, settings.frameRate());
        assertTrue(settings.setFrameRate(GameSettings.MIN_FRAME_RATE));
        assertTrue(settings.setFrameRate(GameSettings.UNLIMITED_FRAME_RATE));
        assertEquals(GameSettings.UNLIMITED_FRAME_RATE, settings.frameRate());
        assertThrows(IllegalArgumentException.class, () -> settings.setFrameRate(GameSettings.MIN_FRAME_RATE - 1));
        assertThrows(IllegalArgumentException.class, () -> settings.setFrameRate(GameSettings.MAX_FRAME_RATE + 1));
    }
}
