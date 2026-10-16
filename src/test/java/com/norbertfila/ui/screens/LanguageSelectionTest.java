package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.ui.*;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class LanguageSelectionTest {
    @Test
    void pointerAndKeyboardSwitchLanguageWithoutChangingOtherSettingsOrRecreatingViews() {
        var settings = new GameSettings();
        var screen = new SettingsScreen(settings);
        var menu = new MainMenuScreen(false, settings.translations());
        var form = new CreateWorldScreen(settings.translations());
        var viewport = new UiViewport(1280, 720, 1280, 720);
        FloatBuffer englishMenu = render(menu, viewport);
        assertEquals("Enter a world name", form.validationMessage());
        screen.layout(viewport);
        UiBounds language = screen.languageButton().bounds();
        assertTrue(language.y() + language.height() <= viewport.height());
        UiBounds back = screen.backButton().bounds();
        assertTrue(back.y() + back.height() <= viewport.height());
        screen.updatePointer(false, false, 0, 0);
        screen.updatePointer(true, true, language.x() + 1, language.y() + 1);
        assertEquals(Language.ENGLISH, settings.translations().language());
        screen.updatePointer(false, false, language.x() + 1, language.y() + 1);
        assertEquals(Language.POLISH, settings.translations().language());
        assertEquals("Podaj nazwę świata", form.validationMessage());
        assertNotEquals(englishMenu, render(menu, viewport));
        assertEquals(8, settings.renderDistance());
        assertEquals(GameSettings.DEFAULT_FIELD_OF_VIEW, settings.fieldOfView());
        assertEquals(GameSettings.GUI_SCALE_AUTO, settings.guiScale());
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(Language.ENGLISH, settings.translations().language());
        assertEquals(englishMenu, render(menu, viewport));
    }

    @Test
    void pauseAndMainMenuExposeOptionsToKeyboardNavigation() {
        var main = new MainMenuScreen(false);
        main.keyPress(UiKey.NEXT);
        main.keyPress(UiKey.NEXT);
        assertTrue(main.optionsButton().focused());
        assertEquals(Screen.Action.OPTIONS, main.keyPress(UiKey.ACTIVATE));
        var pause = new PauseScreen();
        pause.keyPress(UiKey.NEXT);
        pause.keyPress(UiKey.NEXT);
        pause.keyPress(UiKey.NEXT);
        assertTrue(pause.optionsButton().focused());
        assertEquals(Screen.Action.OPTIONS, pause.keyPress(UiKey.ACTIVATE));
    }

    @Test
    void translatedSettingsLabelsFitBesideTheirControls() {
        var translations = new com.norbertfila.i18n.Translations(Language.ENGLISH);
        for (Language language : Language.values()) {
            translations.setLanguage(language);
            for (String key :
                    new String[] {"settings.simulation", "settings.fov", "settings.zoomFov", "settings.zoomSpeed"}) {
                assertTrue(BitmapFont.DEFAULT.width(translations.text(key, 110)) <= 128, language + ": " + key);
            }
            assertTrue(BitmapFont.DEFAULT.width(translations.text("settings.sensitivity", 1f)) <= 128);
            assertTrue(BitmapFont.DEFAULT.width(translations.text("settings.renderDistance", 32)) <= 280);
        }
    }

    private static FloatBuffer render(Screen screen, UiViewport viewport) {
        var canvas = new UiCanvas();
        screen.render(canvas, viewport);
        FloatBuffer result = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(result);
        return result.flip();
    }
}
