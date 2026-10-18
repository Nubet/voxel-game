package com.norbertfila.persistence;

import com.norbertfila.core.GameSettings;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class FrameRatePreferences {
    private static final Logger LOG = Logger.getLogger(FrameRatePreferences.class.getName());
    private final String nodePath;

    public FrameRatePreferences() {
        this("com/norbertfila/voxel-engine/frame-rate");
    }

    FrameRatePreferences(String nodePath) {
        this.nodePath = Objects.requireNonNull(nodePath);
    }

    public void loadInto(GameSettings settings) {
        Objects.requireNonNull(settings);
        try {
            var node = Preferences.userRoot().node(nodePath);
            int value = node.getInt("limit", GameSettings.DEFAULT_FRAME_RATE);
            if (value != GameSettings.UNLIMITED_FRAME_RATE
                    && (value < GameSettings.MIN_FRAME_RATE || value > GameSettings.MAX_FRAME_RATE))
                value = GameSettings.DEFAULT_FRAME_RATE;
            settings.setFrameRate(value);
        } catch (SecurityException failure) {
            LOG.log(Level.WARNING, "Cannot read frame rate preferences", failure);
        }
    }

    public void save(GameSettings settings) {
        Objects.requireNonNull(settings);
        try {
            var node = Preferences.userRoot().node(nodePath);
            node.putInt("limit", settings.frameRate());
            node.flush();
        } catch (SecurityException | BackingStoreException failure) {
            LOG.log(Level.WARNING, "Cannot save frame rate preferences", failure);
        }
    }
}
