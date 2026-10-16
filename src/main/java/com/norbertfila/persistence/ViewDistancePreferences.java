package com.norbertfila.persistence;

import com.norbertfila.core.GameSettings;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class ViewDistancePreferences {
    private static final Logger LOG = Logger.getLogger(ViewDistancePreferences.class.getName());
    private final String nodePath;

    public ViewDistancePreferences() {
        this("com/norbertfila/voxel-engine/view-distances");
    }

    ViewDistancePreferences(String nodePath) {
        this.nodePath = Objects.requireNonNull(nodePath);
    }

    public void loadInto(GameSettings settings) {
        Objects.requireNonNull(settings);
        try {
            var node = Preferences.userRoot().node(nodePath);
            int detail = read(
                    node,
                    "detail",
                    GameSettings.MIN_RENDER_DISTANCE,
                    GameSettings.MAX_RENDER_DISTANCE,
                    GameSettings.DEFAULT_RENDER_DISTANCE);
            int simulation = read(
                    node,
                    "simulation",
                    GameSettings.MIN_SIMULATION_DISTANCE,
                    detail,
                    Math.min(GameSettings.DEFAULT_SIMULATION_DISTANCE, detail));
            int landscape =
                    read(node, "landscape", 0, GameSettings.MAX_FAR_DISTANCE, GameSettings.DEFAULT_FAR_DISTANCE);
            settings.setRenderDistance(detail);
            settings.setSimulationDistance(simulation);
            settings.setFarDistance(landscape);
        } catch (SecurityException failure) {
            LOG.log(Level.WARNING, "Cannot read view distance preferences", failure);
        }
    }

    private static int read(Preferences node, String key, int min, int max, int fallback) {
        int value = node.getInt(key, fallback);
        return value >= min && value <= max ? value : fallback;
    }

    public void save(GameSettings settings) {
        Objects.requireNonNull(settings);
        try {
            var node = Preferences.userRoot().node(nodePath);
            node.putInt("detail", settings.renderDistance());
            node.putInt("simulation", settings.simulationDistance());
            node.putInt("landscape", settings.farDistance());
            node.flush();
        } catch (SecurityException | BackingStoreException failure) {
            LOG.log(Level.WARNING, "Cannot save view distance preferences", failure);
        }
    }
}
