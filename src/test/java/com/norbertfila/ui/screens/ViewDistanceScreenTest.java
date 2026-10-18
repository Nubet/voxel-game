package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.ui.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ViewDistanceScreenTest {
    @Test
    void keyboardControlsAllDistancesAndClampsSimulationWithoutChangingLandscape() {
        var settings = new GameSettings();
        var screen = new ViewDistanceScreen(settings);
        assertEquals(0, settings.farDistance());
        screen.keyPress(UiKey.NEXT);
        for (int i = 0; i < 8; i++) screen.keyPress(UiKey.DECREASE);
        assertEquals(4, settings.renderDistance());
        assertEquals(4, settings.simulationDistance());
        assertEquals(0, settings.farDistance());
        screen.keyPress(UiKey.NEXT);
        screen.keyPress(UiKey.DECREASE);
        assertEquals(3, settings.simulationDistance());
        screen.keyPress(UiKey.NEXT);
        for (int i = 0; i < 32; i++) screen.keyPress(UiKey.DECREASE);
        assertEquals(0, settings.farDistance());
        screen.keyPress(UiKey.INCREASE);
        assertEquals(1, settings.farDistance());
        screen.keyPress(UiKey.NEXT);
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.ACTIVATE));
    }

    @ParameterizedTest
    @CsvSource({"1280,720,1280,720", "640,360,1280,720", "640,480,1280,720", "853,601,1281,903"})
    void pointerHitTestingMatchesCenteredLayoutAcrossDpi(int ww, int wh, int fw, int fh) {
        var settings = new GameSettings();
        var screen = new ViewDistanceScreen(settings);
        var viewport = new UiViewport(ww, wh, fw, fh);
        screen.layout(viewport);
        screen.updatePointer(false, false, 0, 0);
        var bounds = screen.landscapeSlider().bounds();
        double windowX = (bounds.x() + bounds.width() - .25) * viewport.scale() * ww / fw;
        double windowY = (bounds.y() + 1) * viewport.scale() * wh / fh;
        screen.updatePointer(true, true, viewport.cursorX(windowX), viewport.cursorY(windowY));
        assertEquals(128, settings.farDistance());
        screen.cancelInteraction();
        screen.updatePointer(true, true, bounds.x(), bounds.y());
        assertEquals(128, settings.farDistance());
        screen.updatePointer(false, false, 0, 0);
        screen.updatePointer(true, true, bounds.x(), bounds.y());
        assertEquals(0, settings.farDistance());
    }

    @Test
    void labelsFitInBothLanguagesIncludingInactiveAndLowDetailStates() {
        var settings = new GameSettings();
        var screen = new ViewDistanceScreen(settings);
        for (Language language : Language.values()) {
            settings.translations().setLanguage(language);
            for (String key : new String[] {
                "distance.detail.help",
                "distance.simulation.help",
                "distance.landscape.help",
                "distance.landscape.off",
                "distance.inactive",
                "distance.popIn"
            }) assertTrue(BitmapFont.DEFAULT.width(settings.translations().text(key)) <= 280, language + ": " + key);
            for (String key : new String[] {"distance.detail", "distance.simulation", "distance.landscape"})
                assertTrue(
                        BitmapFont.DEFAULT.width(settings.translations().text(key, 128, 2048)) <= 280,
                        language + ": " + key);
            for (int far : new int[] {0, 4, 32, 128}) {
                settings.setRenderDistance(4);
                settings.setFarDistance(far);
                var canvas = new UiCanvas();
                screen.render(canvas, new UiViewport(640, 360, 640, 360));
                assertTrue(canvas.vertexCount() > 0);
                assertEquals(far, settings.farDistance());
            }
        }
    }
}
