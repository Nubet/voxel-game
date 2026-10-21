package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.ui.*;
import org.junit.jupiter.api.Test;

class DisplayScreenTest {
    @Test
    void keyboardAdjustsEachValueAndResetRestoresDefaults() {
        var settings = new GameSettings();
        var screen = new DisplayScreen(settings);
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.INCREASE);
        assertEquals(GameSettings.DEFAULT_BRIGHTNESS + 1, settings.brightness());
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.DECREASE);
        assertEquals(GameSettings.DEFAULT_CONTRAST - 1, settings.contrast());
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.INCREASE);
        assertEquals(GameSettings.DEFAULT_GAMMA + 5, settings.gamma());
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(GameSettings.DEFAULT_BRIGHTNESS, settings.brightness());
        assertEquals(GameSettings.DEFAULT_CONTRAST, settings.contrast());
        assertEquals(GameSettings.DEFAULT_GAMMA, settings.gamma());
        screen.keyPress(UiKey.NEXT);
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.ACTIVATE));
    }

    @Test
    void pointerDragsSliderToItsEnd() {
        var settings = new GameSettings();
        var screen = new DisplayScreen(settings);
        screen.layout(new UiViewport(640, 360, 640, 360));
        screen.updatePointer(false, false, 0, 0);
        var bounds = screen.gammaSlider().bounds();
        screen.updatePointer(true, true, bounds.x() + bounds.width() - 1, bounds.y() + 1);
        assertEquals(GameSettings.MAX_GAMMA, settings.gamma());
    }

    @Test
    void localizedLabelsFitTheirButtons() {
        var settings = new GameSettings();
        var screen = new DisplayScreen(settings);
        for (Language language : Language.values()) {
            settings.translations().setLanguage(language);
            screen.render(new UiCanvas(), new UiViewport(640, 360, 640, 360));
            assertTrue(BitmapFont.DEFAULT.width(settings.translations().text("display.reset")) + 8
                    <= screen.resetButton().bounds().width());
            assertTrue(BitmapFont.DEFAULT.width(settings.translations().text("display.help")) + 24 <= 304);
        }
    }
}
