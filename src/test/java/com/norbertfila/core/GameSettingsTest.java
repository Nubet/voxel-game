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

        assertEquals(4, settings.renderDistance());
        assertFalse(settings.setRenderDistance(4));
        assertTrue(settings.setRenderDistance(16));
        assertEquals(16, settings.renderDistance());
    }

    @Test
    void rejectsDistanceOutsideGuiRange() {
        GameSettings settings = new GameSettings();

        assertThrows(IllegalArgumentException.class, () -> settings.setRenderDistance(1));
        assertThrows(IllegalArgumentException.class, () -> settings.setRenderDistance(17));
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
}
