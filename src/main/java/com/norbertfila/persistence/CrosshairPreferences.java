package com.norbertfila.persistence;

import com.norbertfila.ui.crosshair.CrosshairSettings;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class CrosshairPreferences {
    private static final Logger LOG = Logger.getLogger(CrosshairPreferences.class.getName());
    private final String nodePath;

    public CrosshairPreferences() {
        this("com/norbertfila/voxel-engine/crosshair");
    }

    CrosshairPreferences(String nodePath) {
        this.nodePath = Objects.requireNonNull(nodePath);
    }

    public CrosshairSettings load() {
        var defaults = CrosshairSettings.DEFAULT;
        try {
            var node = Preferences.userRoot().node(nodePath);
            int scale = node.getInt("scale", defaults.scale());
            int opacity = node.getInt("opacity", defaults.opacity());
            return new CrosshairSettings(
                    node.getBoolean("dynamic", defaults.dynamic()),
                    node.getBoolean("hideWithoutTarget", defaults.hideWithoutTarget()),
                    node.getBoolean("smooth", defaults.smooth()),
                    scale >= CrosshairSettings.MIN_SCALE && scale <= CrosshairSettings.MAX_SCALE
                            ? scale
                            : defaults.scale(),
                    opacity >= 0 && opacity <= 100 ? opacity : defaults.opacity());
        } catch (SecurityException failure) {
            LOG.log(Level.WARNING, "Cannot read crosshair preferences", failure);
            return defaults;
        }
    }

    public void save(CrosshairSettings settings) {
        Objects.requireNonNull(settings);
        try {
            var node = Preferences.userRoot().node(nodePath);
            node.putBoolean("dynamic", settings.dynamic());
            node.putBoolean("hideWithoutTarget", settings.hideWithoutTarget());
            node.putBoolean("smooth", settings.smooth());
            node.putInt("scale", settings.scale());
            node.putInt("opacity", settings.opacity());
            node.flush();
        } catch (SecurityException | BackingStoreException failure) {
            LOG.log(Level.WARNING, "Cannot save crosshair preferences", failure);
        }
    }
}
