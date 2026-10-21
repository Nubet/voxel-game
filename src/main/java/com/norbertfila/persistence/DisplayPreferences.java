package com.norbertfila.persistence;

import com.norbertfila.core.GameSettings;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class DisplayPreferences {
    private static final Logger LOG = Logger.getLogger(DisplayPreferences.class.getName());
    private final String nodePath;

    public DisplayPreferences() {
        this("com/norbertfila/voxel-engine/display");
    }

    DisplayPreferences(String nodePath) {
        this.nodePath = Objects.requireNonNull(nodePath);
    }

    public void loadInto(GameSettings settings) {
        Objects.requireNonNull(settings);
        try {
            var node = Preferences.userRoot().node(nodePath);
            settings.setBrightness(read(
                    node,
                    "brightness",
                    GameSettings.MIN_BRIGHTNESS,
                    GameSettings.MAX_BRIGHTNESS,
                    GameSettings.DEFAULT_BRIGHTNESS));
            settings.setContrast(read(
                    node,
                    "contrast",
                    GameSettings.MIN_CONTRAST,
                    GameSettings.MAX_CONTRAST,
                    GameSettings.DEFAULT_CONTRAST));
            settings.setGamma(
                    read(node, "gamma", GameSettings.MIN_GAMMA, GameSettings.MAX_GAMMA, GameSettings.DEFAULT_GAMMA));
        } catch (SecurityException failure) {
            LOG.log(Level.WARNING, "Cannot read display preferences", failure);
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
            node.putInt("brightness", settings.brightness());
            node.putInt("contrast", settings.contrast());
            node.putInt("gamma", settings.gamma());
            node.flush();
        } catch (SecurityException | BackingStoreException failure) {
            LOG.log(Level.WARNING, "Cannot save display preferences", failure);
        }
    }
}
