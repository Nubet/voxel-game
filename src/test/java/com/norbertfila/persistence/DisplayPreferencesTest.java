package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class DisplayPreferencesTest {
    @Test
    void roundTripsDisplayValuesAndFallsBackOnInvalidOnes() throws Exception {
        String path = "com/norbertfila/tests/display/" + UUID.randomUUID();
        var node = Preferences.userRoot().node(path);
        try {
            var settings = new GameSettings();
            var store = new DisplayPreferences(path);
            store.loadInto(settings);
            assertEquals(GameSettings.DEFAULT_BRIGHTNESS, settings.brightness());
            assertEquals(GameSettings.DEFAULT_CONTRAST, settings.contrast());
            assertEquals(GameSettings.DEFAULT_GAMMA, settings.gamma());
            settings.setBrightness(80);
            settings.setContrast(120);
            settings.setGamma(180);
            store.save(settings);
            var loaded = new GameSettings();
            new DisplayPreferences(path).loadInto(loaded);
            assertEquals(80, loaded.brightness());
            assertEquals(120, loaded.contrast());
            assertEquals(180, loaded.gamma());
            node.putInt("brightness", 101);
            node.put("contrast", "invalid");
            node.putInt("gamma", 49);
            store.loadInto(loaded);
            assertEquals(GameSettings.DEFAULT_BRIGHTNESS, loaded.brightness());
            assertEquals(GameSettings.DEFAULT_CONTRAST, loaded.contrast());
            assertEquals(GameSettings.DEFAULT_GAMMA, loaded.gamma());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }
}
