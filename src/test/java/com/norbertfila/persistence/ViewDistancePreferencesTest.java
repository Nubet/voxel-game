package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class ViewDistancePreferencesTest {
    @Test
    void roundTripsAllDistancesIncludingDisabledAndOverlappedLandscape() throws Exception {
        String path = "com/norbertfila/tests/view-distances/" + UUID.randomUUID();
        var node = Preferences.userRoot().node(path);
        try {
            var settings = new GameSettings();
            var store = new ViewDistancePreferences(path);
            store.loadInto(settings);
            assertEquals(12, settings.renderDistance());
            assertEquals(12, settings.simulationDistance());
            assertEquals(0, settings.farDistance());
            for (int landscape : new int[] {0, 2, 32, 128}) {
                settings.setRenderDistance(4);
                settings.setSimulationDistance(3);
                settings.setFarDistance(landscape);
                store.save(settings);
                var loaded = new GameSettings();
                new ViewDistancePreferences(path).loadInto(loaded);
                assertEquals(4, loaded.renderDistance());
                assertEquals(3, loaded.simulationDistance());
                assertEquals(landscape, loaded.farDistance());
            }
            node.putInt("detail", 4);
            node.putInt("simulation", 32);
            node.put("landscape", "invalid");
            store.loadInto(settings);
            assertEquals(4, settings.renderDistance());
            assertEquals(4, settings.simulationDistance());
            assertEquals(0, settings.farDistance());
            node.putInt("detail", -1);
            node.putInt("simulation", 2);
            node.putInt("landscape", 129);
            store.loadInto(settings);
            assertEquals(12, settings.renderDistance());
            assertEquals(2, settings.simulationDistance());
            assertEquals(0, settings.farDistance());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }
}
