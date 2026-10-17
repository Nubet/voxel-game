package com.norbertfila.persistence;

import com.norbertfila.player.visual.model.ModelVariant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class AppearancePreferences {
    private static final Logger LOG = Logger.getLogger(AppearancePreferences.class.getName());
    private final String nodePath;

    public AppearancePreferences() {
        this("com/norbertfila/voxel-engine/appearance");
    }

    AppearancePreferences(String nodePath) {
        this.nodePath = Objects.requireNonNull(nodePath);
    }

    public ModelVariant load() {
        try {
            return ModelVariant.valueOf(
                    Preferences.userRoot().node(nodePath).get("variant", ModelVariant.CLASSIC.name()));
        } catch (IllegalArgumentException | SecurityException failure) {
            LOG.log(Level.WARNING, "Cannot read appearance preferences", failure);
            return ModelVariant.CLASSIC;
        }
    }

    public void save(ModelVariant variant) {
        Objects.requireNonNull(variant);
        try {
            var node = Preferences.userRoot().node(nodePath);
            node.put("variant", variant.name());
            node.flush();
        } catch (SecurityException | BackingStoreException failure) {
            LOG.log(Level.WARNING, "Cannot save appearance preferences", failure);
        }
    }
}
