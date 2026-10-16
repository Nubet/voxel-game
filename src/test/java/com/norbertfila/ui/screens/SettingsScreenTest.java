package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.render.CloudMode;
import com.norbertfila.ui.*;
import org.junit.jupiter.api.Test;

class SettingsScreenTest {
    @Test
    void distancesAreSavedWhenLeavingChildAndSettings() {
        var settings = new GameSettings();
        int[] saves = {0};
        var screen = new SettingsScreen(settings, ignored -> {}, () -> saves[0]++);
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.ACTIVATE);
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.INCREASE);
        assertEquals(0, saves[0]);
        screen.keyPress(UiKey.BACK);
        assertEquals(1, saves[0]);
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
        assertEquals(2, saves[0]);
    }

    @Test
    void distancesOpenOnReleaseAndBackReturnsToSettingsBeforeClosing() {
        var settings = new GameSettings();
        var screen = new SettingsScreen(settings);
        screen.keyPress(UiKey.NEXT);
        assertTrue(screen.distancesButton().focused());
        screen.keyPress(UiKey.ACTIVATE);
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.INCREASE);
        assertEquals(9, settings.renderDistance());
        assertEquals(Screen.Action.NONE, screen.keyPress(UiKey.BACK));
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
    }

    @Test
    void cameraControlsAndCloudsStillWorkWithKeyboardAndPointer() {
        var settings = new GameSettings();
        var screen = new SettingsScreen(settings);
        screen.updatePointer(true, true, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.FOV_Y);
        screen.updatePointer(false, false, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.FOV_Y);
        assertEquals(GameSettings.MAX_FIELD_OF_VIEW, settings.fieldOfView());
        screen.updatePointer(
                true, true, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.ZOOM_FOV_Y);
        screen.updatePointer(
                false, false, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.ZOOM_FOV_Y);
        assertEquals(settings.fieldOfView(), settings.zoomFieldOfView());
        var bounds = screen.cloudsButton().bounds();
        screen.updatePointer(true, true, bounds.x() + 1, bounds.y() + 1);
        assertEquals(CloudMode.FANCY, settings.clouds());
        screen.updatePointer(false, false, bounds.x() + 1, bounds.y() + 1);
        assertEquals(CloudMode.OFF, settings.clouds());
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(CloudMode.FAST, settings.clouds());
        screen.cancelInteraction();
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.backButton().focused());
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.crosshairButton().focused());
    }

    @Test
    void localizedLabelsFitAndLayoutRemainsInsidePanel() {
        var settings = new GameSettings();
        var screen = new SettingsScreen(settings);
        for (Language language : Language.values()) {
            settings.translations().setLanguage(language);
            var canvas = new UiCanvas();
            screen.render(canvas, new UiViewport(640, 360, 640, 360));
            assertTrue(canvas.vertexCount() > 54);
            assertTrue(BitmapFont.DEFAULT.width(settings.translations().text("distance.title")) + 8
                    < screen.distancesButton().bounds().width());
            for (String key : new String[] {
                "settings.auto",
                "settings.size.small",
                "settings.size.medium",
                "settings.size.large",
                "settings.size.extraLarge"
            })
                assertTrue(BitmapFont.DEFAULT.width(settings.translations().text(key)) + 8
                        <= screen.guiScaleButton().bounds().width());
            assertTrue(screen.guiScaleButton().bounds().x()
                            + screen.guiScaleButton().bounds().width()
                    < screen.cloudsButton().bounds().x());
        }
    }
}
