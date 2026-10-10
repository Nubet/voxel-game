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
}
