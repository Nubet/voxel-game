package com.norbertfila.persistence;

import com.norbertfila.i18n.Language;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class LanguagePreferences {
    private static final Logger LOG = Logger.getLogger(LanguagePreferences.class.getName());

    public Language load() {
        try {
            return Language.fromCode(node().get("language", Language.ENGLISH.code()));
        } catch (SecurityException failure) {
            LOG.log(Level.WARNING, "Cannot read language preference", failure);
            return Language.ENGLISH;
        }
    }

    public void save(Language language) {
        try {
            Preferences preferences = node();
            preferences.put("language", language.code());
            preferences.flush();
        } catch (SecurityException | BackingStoreException failure) {
            LOG.log(Level.WARNING, "Cannot save language preference", failure);
        }
    }

    private Preferences node() {
        return Preferences.userRoot().node("com/norbertfila/voxel-engine");
    }
}
