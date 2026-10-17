package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.player.visual.model.ModelVariant;
import com.norbertfila.ui.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AppearanceScreenTest {
    @Test
    void settingsKeyboardOpensChildCyclesVariantAndSavesOnBack() {
        var settings = new GameSettings();
        var saved = new ArrayList<ModelVariant>();
        var screen = new SettingsScreen(settings, ignored -> {}, () -> {}, null, saved::add);
        screen.keyPress(UiKey.PREVIOUS);
        screen.keyPress(UiKey.PREVIOUS);
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.appearanceButton().focused());
        screen.keyPress(UiKey.ACTIVATE);
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(ModelVariant.SLIM, settings.modelVariant());
        assertEquals(Screen.Action.NONE, screen.keyPress(UiKey.BACK));
        assertEquals(java.util.List.of(ModelVariant.SLIM), saved);
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
    }

    @Test
    void childCyclesWrapsFocusAndDoesNotClickThroughAfterCancellation() {
        var settings = new GameSettings();
        var screen = new AppearanceScreen(settings, null);
        screen.cancelInteraction();
        screen.updatePointer(true, true, 20, 40);
        screen.updatePointer(false, false, 20, 40);
        assertEquals(ModelVariant.CLASSIC, settings.modelVariant());
        screen.updatePointer(true, true, 20, 40);
        screen.updatePointer(false, false, 20, 40);
        assertEquals(ModelVariant.SLIM, settings.modelVariant());
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(ModelVariant.CLASSIC, settings.modelVariant());
        screen.cancelInteraction();
        screen.keyPress(UiKey.PREVIOUS);
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.ACTIVATE));
    }

    @Test
    void appearanceRendersBothTranslationsAcrossGuiScales() {
        var settings = new GameSettings();
        var screen = new AppearanceScreen(settings, null);
        for (var language : Language.values()) {
            settings.translations().setLanguage(language);
            for (int scale = 0; scale <= 4; scale++) {
                var viewport = new UiViewport(1280, 960, 1280, 960, scale);
                screen.render(new UiCanvas(), viewport);
            }
            assertTrue(BitmapFont.DEFAULT.width(settings.translations().text("appearance.metadata")) <= 280);
        }
    }
}
