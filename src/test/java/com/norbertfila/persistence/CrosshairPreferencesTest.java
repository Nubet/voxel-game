package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.crosshair.CrosshairSettings;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class CrosshairPreferencesTest {
    @Test
    void roundTripsSettingsAndRecoversInvalidFieldsWithoutLosingValidOnes() throws Exception {
        String path = "com/norbertfila/tests/crosshair/" + UUID.randomUUID();
        var node = Preferences.userRoot().node(path);
        try {
            var store = new CrosshairPreferences(path);
            assertEquals(CrosshairSettings.DEFAULT, store.load());
            var settings = new CrosshairSettings(false, false, false, 4, 37);
            store.save(settings);
            assertEquals(settings, new CrosshairPreferences(path).load());
            node.putInt("scale", 99);
            node.put("opacity", "invalid");
            assertEquals(new CrosshairSettings(false, false, false, 1, 100), store.load());
            node.putInt("opacity", -1);
            node.put("dynamic", "invalid");
            assertTrue(store.load().dynamic());
            assertEquals(100, store.load().opacity());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }
}
