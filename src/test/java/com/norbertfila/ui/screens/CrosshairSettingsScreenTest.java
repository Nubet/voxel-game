package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.ui.*;
import com.norbertfila.ui.crosshair.CrosshairSettings;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CrosshairSettingsScreenTest {
    @Test
    void keyboardChangesSettingsAndPreviewDoesNotChangeGameplay() {
        var settings = new GameSettings();
        var screen = new CrosshairSettingsScreen(settings);
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.ACTIVATE);
        assertFalse(settings.crosshair().hideWithoutTarget());
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.ACTIVATE);
        assertFalse(settings.crosshair().smooth());
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.INCREASE);
        assertEquals(2, settings.crosshair().scale());
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.DECREASE);
        assertEquals(99, settings.crosshair().opacity());
        screen.keyPress(UiKey.NEXT);
        var before = settings.crosshair();
        for (int i = 0; i < 5; i++) screen.keyPress(UiKey.ACTIVATE);
        assertEquals(before, settings.crosshair());
        screen.keyPress(UiKey.NEXT);
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.ACTIVATE));
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
    }

    @Test
    void subpageReturnsToParentAndSavesExactlyOnceWithoutClosingSettings() {
        var settings = new GameSettings();
        var saved = new ArrayList<CrosshairSettings>();
        var screen = new SettingsScreen(settings, saved::add);
        var viewport = new UiViewport(1280, 720, 1280, 720);
        screen.layout(viewport);
        var button = screen.crosshairButton().bounds();
        screen.updatePointer(false, false, 0, 0);
        screen.updatePointer(true, true, button.x() + 1, button.y() + 1);
        assertEquals(Screen.Action.NONE, screen.updatePointer(false, false, button.x() + 1, button.y() + 1));
        screen.layout(viewport);
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.ACTIVATE);
        assertFalse(settings.crosshair().hideWithoutTarget());
        assertEquals(Screen.Action.NONE, screen.keyPress(UiKey.BACK));
        assertEquals(java.util.List.of(settings.crosshair()), saved);
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
        assertEquals(1, saved.size());
    }

    @Test
    void pointerCancellationAndBothTranslationsWorkAtAllGuiScales() {
        var settings = new GameSettings();
        var screen = new CrosshairSettingsScreen(settings);
        for (var language : Language.values()) {
            settings.translations().setLanguage(language);
            for (int scale = 0; scale <= 4; scale++) {
                var viewport = new UiViewport(1280, 720, 1280, 720, scale);
                screen.render(new UiCanvas(), viewport);
            }
        }
        var viewport = new UiViewport(640, 480, 640, 480, 1);
        screen.layout(viewport);
        int x = (640 - 304) / 2 + 215, y = (480 - 240) / 2 + 55;
        screen.updatePointer(false, false, x, y);
        screen.updatePointer(true, true, x, y);
        screen.cancelInteraction();
        screen.updatePointer(false, false, x, y);
        assertTrue(settings.crosshair().hideWithoutTarget());
        screen.updatePointer(true, true, x, y);
        screen.updatePointer(false, false, x, y);
        assertFalse(settings.crosshair().hideWithoutTarget());
    }
}
