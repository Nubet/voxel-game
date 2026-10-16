package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.render.CloudMode;
import com.norbertfila.ui.*;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsScreenTest {
    @ParameterizedTest
    @CsvSource({"1280,720,1280,720", "640,360,1280,720", "1280,720,1920,1080", "640,480,1280,720", "853,601,1281,903"})
    void sliderHitTestingMatchesRenderingAcrossDpi(int ww, int wh, int fw, int fh) {
        UiViewport viewport = new UiViewport(ww, wh, fw, fh);
        SettingsScreen screen = new SettingsScreen(new GameSettings());
        double windowY = (SettingsScreen.SLIDER_Y + 1) * viewport.scale() * (double) wh / fh;
        for (double x :
                new double[] {SettingsScreen.SLIDER_X + .25, SettingsScreen.SLIDER_X + SettingsScreen.SLIDER_WIDTH - .25
                }) {
            double windowX = x * viewport.scale() * ww / fw;
            assertTrue(update(screen, true, true, viewport.cursorX(windowX), viewport.cursorY(windowY)));
            assertEquals(
                    x < SettingsScreen.SLIDER_X + 1
                            ? GameSettings.MIN_RENDER_DISTANCE
                            : GameSettings.MAX_RENDER_DISTANCE,
                    screen.renderDistance());
        }
        assertFalse(update(screen, true, true, SettingsScreen.SLIDER_X - 7, SettingsScreen.SLIDER_Y));
        assertEquals(GameSettings.MAX_RENDER_DISTANCE, screen.renderDistance());
    }

    @Test
    void sliderEdgesAndButtonsUseValidatedSettingsAndReleaseActivation() {
        GameSettings settings = new GameSettings();
        SettingsScreen screen = new SettingsScreen(settings);
        assertFalse(update(screen, true, true, SettingsScreen.PLUS_X + 1, SettingsScreen.BUTTON_Y + 1));
        assertEquals(4, settings.renderDistance());
        assertFalse(update(screen, true, false, SettingsScreen.PLUS_X + 1, SettingsScreen.BUTTON_Y + 1));
        assertTrue(update(screen, false, false, SettingsScreen.PLUS_X + 1, SettingsScreen.BUTTON_Y + 1));
        assertEquals(5, settings.renderDistance());
        assertFalse(update(screen, false, false, SettingsScreen.PLUS_X + 1, SettingsScreen.BUTTON_Y + 1));
        assertTrue(update(screen, true, true, SettingsScreen.SLIDER_X, SettingsScreen.SLIDER_Y));
        assertEquals(GameSettings.MIN_RENDER_DISTANCE, settings.renderDistance());
        assertTrue(update(
                screen, true, true, SettingsScreen.SLIDER_X + SettingsScreen.SLIDER_WIDTH, SettingsScreen.SLIDER_Y));
        assertEquals(GameSettings.MAX_RENDER_DISTANCE, settings.renderDistance());
        assertFalse(update(screen, true, true, SettingsScreen.PLUS_X + 1, SettingsScreen.BUTTON_Y + 1));
        assertFalse(update(screen, false, false, SettingsScreen.PLUS_X + 1, SettingsScreen.BUTTON_Y + 1));
    }

    @Test
    void keyboardNavigationSkipsDisabledButtonsAndIncludesBack() {
        GameSettings settings = new GameSettings();
        settings.setRenderDistance(GameSettings.MIN_RENDER_DISTANCE);
        SettingsScreen screen = new SettingsScreen(settings);
        assertEquals(Screen.Action.NONE, screen.keyPress(UiKey.NEXT));
        assertTrue(screen.slider().focused());
        screen.keyPress(UiKey.INCREASE);
        assertEquals(3, settings.renderDistance());
        screen.keyPress(UiKey.NEXT);
        assertTrue(screen.increaseButton().focused());
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(4, settings.renderDistance());
        screen.keyPress(UiKey.PREVIOUS);
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.decreaseButton().focused());
        screen.keyPress(UiKey.ACTIVATE);
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(2, settings.renderDistance());
        assertFalse(screen.decreaseButton().focused());
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(2, settings.renderDistance());
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.backButton().focused());
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.ACTIVATE));
        screen.cancelInteraction();
        settings.setRenderDistance(GameSettings.MAX_RENDER_DISTANCE);
        screen.keyPress(UiKey.PREVIOUS);
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.cloudsButton().focused());
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.guiScaleButton().focused());
        screen.keyPress(UiKey.PREVIOUS);
        assertTrue(screen.zoomSpeedSlider().focused());
    }

    @Test
    void backRequestsCloseOnlyOnReleaseAndEscapeAlwaysRequestsClose() {
        SettingsScreen screen = new SettingsScreen(new GameSettings());
        double x = SettingsScreen.BACK_BOUNDS.x() + 1, y = SettingsScreen.BACK_BOUNDS.y() + 1;
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, true, x, y));
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, false, x, y));
        assertEquals(Screen.Action.CLOSE, screen.updatePointer(false, false, x, y));
        assertEquals(4, screen.renderDistance());
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
    }

    @Test
    void cancellationRequiresReleaseAndDiscardsPendingClicks() {
        SettingsScreen screen = new SettingsScreen(new GameSettings());
        update(screen, true, true, SettingsScreen.SLIDER_X, SettingsScreen.SLIDER_Y);
        assertTrue(screen.slider().dragging());
        screen.cancelInteraction();
        assertFalse(screen.slider().dragging());
        assertFalse(update(screen, true, true, 1000, -100));
        update(screen, false, false, 0, 0);
        assertTrue(update(
                screen, true, true, SettingsScreen.SLIDER_X + SettingsScreen.SLIDER_WIDTH, SettingsScreen.SLIDER_Y));
        update(screen, false, false, SettingsScreen.SLIDER_X + SettingsScreen.SLIDER_WIDTH, SettingsScreen.SLIDER_Y);
        update(screen, true, true, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1);
        screen.cancelInteraction();
        assertFalse(update(screen, false, false, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1));
        assertEquals(GameSettings.MAX_RENDER_DISTANCE, screen.renderDistance());
    }

    @ParameterizedTest
    @ValueSource(ints = {GameSettings.MIN_RENDER_DISTANCE, 4, GameSettings.MAX_RENDER_DISTANCE})
    void renderingShowsControlsAndBackWithoutChangingSettings(int distance) {
        GameSettings settings = new GameSettings();
        settings.setRenderDistance(distance);
        SettingsScreen screen = new SettingsScreen(settings);
        UiCanvas canvas = new UiCanvas();
        screen.render(canvas, new UiViewport(640, 360, 640, 360));
        assertTrue(canvas.vertexCount() > 54);
        FloatBuffer data = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(data);
        assertEquals((640 - SettingsScreen.PANEL_WIDTH) / 2, data.get(0));
        assertEquals((360 - SettingsScreen.PANEL_HEIGHT) / 2, data.get(1));
        assertEquals(
                distance == GameSettings.MIN_RENDER_DISTANCE ? .07f : .15f,
                redAt(data, screen.decreaseButton().bounds()));
        assertEquals(
                distance == GameSettings.MAX_RENDER_DISTANCE ? .07f : .15f,
                redAt(data, screen.increaseButton().bounds()));
        assertEquals(.15f, redAt(data, screen.backButton().bounds()));
        assertEquals(distance, settings.renderDistance());
    }

    @Test
    void cameraControlsUpdateSettingsWithoutReplacingTheScreen() {
        GameSettings settings = new GameSettings();
        SettingsScreen screen = new SettingsScreen(settings);

        screen.updatePointer(true, true, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.FOV_Y);
        screen.updatePointer(false, false, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.FOV_Y);
        assertEquals(GameSettings.MAX_FIELD_OF_VIEW, settings.fieldOfView());

        screen.updatePointer(
                true, true, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.ZOOM_FOV_Y);
        screen.updatePointer(
                false, false, SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH, SettingsScreen.ZOOM_FOV_Y);
        assertEquals(settings.fieldOfView(), settings.zoomFieldOfView());

        screen.updatePointer(true, true, SettingsScreen.CAMERA_X, SettingsScreen.GUI_SCALE_Y + 1);
        screen.updatePointer(false, false, SettingsScreen.CAMERA_X, SettingsScreen.GUI_SCALE_Y + 1);
        assertEquals(1, settings.guiScale());
    }

    @Test
    void simulationSliderEndsAtTheRenderDistance() {
        GameSettings settings = new GameSettings();
        settings.setRenderDistance(10);
        SettingsScreen screen = new SettingsScreen(settings);
        double right = SettingsScreen.CAMERA_X + SettingsScreen.CAMERA_WIDTH;

        screen.updatePointer(true, true, right, SettingsScreen.SIMULATION_Y);
        screen.updatePointer(false, false, right, SettingsScreen.SIMULATION_Y);
        assertEquals(10, settings.simulationDistance());

        screen.updatePointer(true, true, SettingsScreen.CAMERA_X, SettingsScreen.SIMULATION_Y);
        screen.updatePointer(false, false, SettingsScreen.CAMERA_X, SettingsScreen.SIMULATION_Y);
        assertEquals(GameSettings.MIN_SIMULATION_DISTANCE, settings.simulationDistance());

        settings.setSimulationDistance(8);
        screen.updatePointer(true, true, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1);
        screen.updatePointer(false, false, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1);
        screen.updatePointer(true, true, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1);
        screen.updatePointer(false, false, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1);
        screen.updatePointer(true, true, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1);
        screen.updatePointer(false, false, SettingsScreen.MINUS_X + 1, SettingsScreen.BUTTON_Y + 1);
        assertEquals(7, settings.renderDistance());
        assertEquals(7, settings.simulationDistance());
        assertEquals(7, screen.simulationDistanceSlider().value());
    }

    private static boolean update(SettingsScreen screen, boolean down, boolean press, double x, double y) {
        int before = screen.renderDistance();
        assertEquals(Screen.Action.NONE, screen.updatePointer(down, press, x, y));
        return before != screen.renderDistance();
    }

    @Test
    void cloudModeCyclesOnReleaseAndKeyboardWithoutOverlappingGuiScale() {
        GameSettings settings = new GameSettings();
        SettingsScreen screen = new SettingsScreen(settings);
        var bounds = screen.cloudsButton().bounds();
        assertTrue(bounds.x()
                >= screen.guiScaleButton().bounds().x()
                        + screen.guiScaleButton().bounds().width());
        assertEquals(CloudMode.FAST, settings.clouds());
        screen.updatePointer(true, true, bounds.x() + 1, bounds.y() + 1);
        assertEquals(CloudMode.FAST, settings.clouds());
        screen.updatePointer(false, false, bounds.x() + 1, bounds.y() + 1);
        assertEquals(CloudMode.FANCY, settings.clouds());
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(CloudMode.OFF, settings.clouds());
        screen.keyPress(UiKey.ACTIVATE);
        assertEquals(CloudMode.FAST, settings.clouds());
        assertThrows(NullPointerException.class, () -> settings.setClouds(null));
    }

    private static float redAt(FloatBuffer data, UiBounds bounds) {
        for (int offset = 0; offset < data.position(); offset += 48)
            if (data.get(offset) == bounds.x() && data.get(offset + 1) == bounds.y()) return data.get(offset + 2);
        throw new AssertionError("Missing control background");
    }
}
