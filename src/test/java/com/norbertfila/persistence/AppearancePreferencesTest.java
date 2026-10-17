package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.visual.model.ModelVariant;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class AppearancePreferencesTest {
    @Test
    void variantRoundTripsAndUnknownValuesUseClassic() throws Exception {
        String path = "com/norbertfila/tests/appearance-" + UUID.randomUUID();
        var node = Preferences.userRoot().node(path);
        try {
            var preferences = new AppearancePreferences(path);
            assertEquals(ModelVariant.CLASSIC, preferences.load());
            preferences.save(ModelVariant.SLIM);
            assertEquals(ModelVariant.SLIM, new AppearancePreferences(path).load());
            preferences.save(ModelVariant.CLASSIC);
            assertEquals(ModelVariant.CLASSIC, preferences.load());
            node.put("variant", "unknown");
            assertEquals(ModelVariant.CLASSIC, preferences.load());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }
}
