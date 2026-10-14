package com.norbertfila.ui.screens;

import com.norbertfila.core.GameSettings;
import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.Button;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.Slider;
import com.norbertfila.ui.UiBounds;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import java.util.Objects;

public final class SettingsScreen implements Screen {
    public static final int PANEL_X = 24;
    public static final int PANEL_Y = 24;
    public static final int PANEL_WIDTH = 440;
    public static final int PANEL_HEIGHT = 320;
    public static final int SLIDER_X = 84;
    public static final int SLIDER_Y = 104;
    public static final int SLIDER_WIDTH = 200;
    public static final int SLIDER_HEIGHT = 16;
    public static final int BUTTON_Y = 102;
    public static final int BUTTON_SIZE = 20;
    public static final int MINUS_X = 56;
    public static final int PLUS_X = 292;
    public static final UiBounds BACK_BOUNDS = new UiBounds(184, 280, 120, 24);

    public static final int CAMERA_X = 220;
    public static final int CAMERA_WIDTH = 180;
    public static final int FOV_Y = 128;
    public static final int ZOOM_FOV_Y = 160;
    public static final int SENSITIVITY_Y = 192;
    public static final int ZOOM_SPEED_Y = 224;
    public static final int GUI_SCALE_Y = 256;

    private final GameSettings settings;
    private final Button decrease = new Button(new UiBounds(MINUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE), "-");
    private final Button increase = new Button(new UiBounds(PLUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE), "+");
    private final Slider renderDistance;
    private final Slider fieldOfView = new Slider(
            new UiBounds(CAMERA_X, FOV_Y, CAMERA_WIDTH, SLIDER_HEIGHT),
            (int) GameSettings.MIN_FIELD_OF_VIEW,
            (int) GameSettings.MAX_FIELD_OF_VIEW,
            1,
            (int) GameSettings.DEFAULT_FIELD_OF_VIEW);
    private final Slider zoomFieldOfView = new Slider(
            new UiBounds(CAMERA_X, ZOOM_FOV_Y, CAMERA_WIDTH, SLIDER_HEIGHT),
            (int) GameSettings.MIN_ZOOM_FIELD_OF_VIEW,
            (int) GameSettings.DEFAULT_FIELD_OF_VIEW,
            1,
            (int) GameSettings.DEFAULT_ZOOM_FIELD_OF_VIEW);
    private final Slider mouseSensitivity =
            new Slider(new UiBounds(CAMERA_X, SENSITIVITY_Y, CAMERA_WIDTH, SLIDER_HEIGHT), 1, 100, 1, 11);
    private final Slider zoomSpeed =
            new Slider(new UiBounds(CAMERA_X, ZOOM_SPEED_Y, CAMERA_WIDTH, SLIDER_HEIGHT), 1, 30, 1, 12);
    private final Button guiScale = new Button(new UiBounds(CAMERA_X, GUI_SCALE_Y, CAMERA_WIDTH, 24), "Auto");
    private final Button back = new Button(BACK_BOUNDS, "Back");
    private int focusIndex = -1;
    private boolean pointerReady = true;

    public SettingsScreen(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        renderDistance = new Slider(
                new UiBounds(SLIDER_X, SLIDER_Y, SLIDER_WIDTH, SLIDER_HEIGHT),
                GameSettings.MIN_RENDER_DISTANCE,
                GameSettings.MAX_RENDER_DISTANCE,
                1,
                settings.renderDistance());
        syncControls();
    }

    @Override
    public Action updatePointer(boolean mouseDown, boolean mousePressed, double cursorX, double cursorY) {
        syncControls();
        if (!mouseDown) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (mousePressed) focusAt(cursorX, cursorY);
        boolean minus = decrease.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean plus = increase.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean renderDistanceChanged = renderDistance.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean fovChanged = fieldOfView.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean zoomFovChanged = zoomFieldOfView.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean sensitivityChanged = mouseSensitivity.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean zoomSpeedChanged = zoomSpeed.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean scaleChanged = guiScale.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean close = back.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        if (scaleChanged) cycleGuiScale();
        applyControls(
                minus, plus, renderDistanceChanged, fovChanged, zoomFovChanged, sensitivityChanged, zoomSpeedChanged);
        return close ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (key == UiKey.BACK) return Action.CLOSE;
        syncControls();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            focusNext(key == UiKey.NEXT ? 1 : -1);
            return Action.NONE;
        }
        boolean changed = false;
        boolean minus = false;
        boolean plus = false;
        boolean distanceChanged = false;
        boolean fovChanged = false;
        boolean zoomFovChanged = false;
        boolean sensitivityChanged = false;
        boolean zoomSpeedChanged = false;
        boolean close = false;
        switch (focusIndex) {
            case 0 -> {
                changed = decrease.keyPress(key);
                minus = changed;
            }
            case 1 -> {
                changed = renderDistance.keyPress(key);
                distanceChanged = changed;
            }
            case 2 -> {
                changed = increase.keyPress(key);
                plus = changed;
            }
            case 3 -> {
                changed = fieldOfView.keyPress(key);
                fovChanged = changed;
            }
            case 4 -> {
                changed = zoomFieldOfView.keyPress(key);
                zoomFovChanged = changed;
            }
            case 5 -> {
                changed = mouseSensitivity.keyPress(key);
                sensitivityChanged = changed;
            }
            case 6 -> {
                changed = zoomSpeed.keyPress(key);
                zoomSpeedChanged = changed;
            }
            case 7 -> {
                if (key == UiKey.ACTIVATE) {
                    cycleGuiScale();
                    changed = true;
                }
            }
            case 8 -> close = back.keyPress(key);
            default -> {}
        }
        if (changed)
            applyControls(
                    minus, plus, distanceChanged, fovChanged, zoomFovChanged, sensitivityChanged, zoomSpeedChanged);
        return close ? Action.CLOSE : Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        focusIndex = -1;
        pointerReady = false;
        decrease.cancelInteraction();
        increase.cancelInteraction();
        renderDistance.cancelInteraction();
        fieldOfView.cancelInteraction();
        zoomFieldOfView.cancelInteraction();
        mouseSensitivity.cancelInteraction();
        zoomSpeed.cancelInteraction();
        guiScale.cancelInteraction();
        back.cancelInteraction();
    }

    private void applyControls(
            boolean minus,
            boolean plus,
            boolean distanceChanged,
            boolean fovChanged,
            boolean zoomFovChanged,
            boolean sensitivityChanged,
            boolean zoomSpeedChanged) {
        if (minus || plus || distanceChanged) {
            settings.setRenderDistance(
                    minus ? renderDistance() - 1 : plus ? renderDistance() + 1 : renderDistance.value());
        }
        if (fovChanged) settings.setFieldOfView(fieldOfView.value());
        if (zoomFovChanged) settings.setZoomFieldOfView(zoomFieldOfView.value());
        if (sensitivityChanged) settings.setMouseSensitivity(mouseSensitivity.value() / 100f);
        if (zoomSpeedChanged) settings.setZoomSpeed(zoomSpeed.value());
        syncControls();
    }

    private void syncControls() {
        renderDistance.setValue(settings.renderDistance());
        fieldOfView.setValue(Math.round(settings.fieldOfView()));
        zoomFieldOfView.setRange((int) GameSettings.MIN_ZOOM_FIELD_OF_VIEW, Math.round(settings.fieldOfView()), 1);
        zoomFieldOfView.setValue(Math.round(settings.zoomFieldOfView()));
        mouseSensitivity.setValue(Math.round(settings.mouseSensitivity() * 100));
        zoomSpeed.setValue(Math.round(settings.zoomSpeed()));
        decrease.setEnabled(canDecrease());
        increase.setEnabled(canIncrease());
        guiScale.setLabel(guiScaleLabel());
    }

    private void focusAt(double x, double y) {
        if (decrease.enabled() && decrease.bounds().contains(x, y)) focus(0);
        else if (renderDistance.bounds().contains(x, y)) focus(1);
        else if (increase.enabled() && increase.bounds().contains(x, y)) focus(2);
        else if (fieldOfView.bounds().contains(x, y)) focus(3);
        else if (zoomFieldOfView.bounds().contains(x, y)) focus(4);
        else if (mouseSensitivity.bounds().contains(x, y)) focus(5);
        else if (zoomSpeed.bounds().contains(x, y)) focus(6);
        else if (guiScale.bounds().contains(x, y)) focus(7);
        else if (back.bounds().contains(x, y)) focus(8);
        else focus(-1);
    }

    private void focusNext(int direction) {
        int next = focusIndex;
        do next = Math.floorMod(next + direction, 9);
        while ((next == 0 && !decrease.enabled()) || (next == 2 && !increase.enabled()));
        focus(next);
    }

    private void focus(int index) {
        focusIndex = index;
        decrease.setFocused(index == 0);
        renderDistance.setFocused(index == 1);
        increase.setFocused(index == 2);
        fieldOfView.setFocused(index == 3);
        zoomFieldOfView.setFocused(index == 4);
        mouseSensitivity.setFocused(index == 5);
        zoomSpeed.setFocused(index == 6);
        guiScale.setFocused(index == 7);
        back.setFocused(index == 8);
    }

    private void cycleGuiScale() {
        int next = settings.guiScale() == GameSettings.MAX_GUI_SCALE
                ? GameSettings.GUI_SCALE_AUTO
                : settings.guiScale() + 1;
        settings.setGuiScale(next);
        syncControls();
    }

    private String guiScaleLabel() {
        return settings.guiScale() == GameSettings.GUI_SCALE_AUTO ? "Auto" : settings.guiScale() + "x";
    }

    public Slider slider() {
        return renderDistance;
    }

    public Slider fieldOfViewSlider() {
        return fieldOfView;
    }

    public Slider zoomFieldOfViewSlider() {
        return zoomFieldOfView;
    }

    public Slider mouseSensitivitySlider() {
        return mouseSensitivity;
    }

    public Slider zoomSpeedSlider() {
        return zoomSpeed;
    }

    public Button guiScaleButton() {
        return guiScale;
    }

    public Button decreaseButton() {
        return decrease;
    }

    public Button increaseButton() {
        return increase;
    }

    public Button backButton() {
        return back;
    }

    public int renderDistance() {
        return settings.renderDistance();
    }

    public boolean canDecrease() {
        return settings.renderDistance() > GameSettings.MIN_RENDER_DISTANCE;
    }

    public boolean canIncrease() {
        return settings.renderDistance() < GameSettings.MAX_RENDER_DISTANCE;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        syncControls();
        canvas.rectangle(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, .06f, .08f, .11f, .94f);
        BitmapFont font = BitmapFont.DEFAULT;
        canvas.textWithShadow(font, "Settings", PANEL_X + 12, PANEL_Y + 10, 1, 1, 1, 1);
        canvas.textWithShadow(
                font, "Render distance: " + renderDistance() + " chunks", PANEL_X + 12, PANEL_Y + 44, 1, 1, 1, 1);
        canvas.textWithShadow(font, "FOV: " + Math.round(settings.fieldOfView()), 36, FOV_Y - 4, 1, 1, 1, 1);
        canvas.textWithShadow(
                font, "Zoom FOV: " + Math.round(settings.zoomFieldOfView()), 36, ZOOM_FOV_Y - 4, 1, 1, 1, 1);
        canvas.textWithShadow(
                font,
                "Sensitivity: " + String.format(java.util.Locale.ROOT, "%.2f", settings.mouseSensitivity()),
                36,
                SENSITIVITY_Y - 4,
                1,
                1,
                1,
                1);
        canvas.textWithShadow(
                font, "Zoom speed: " + Math.round(settings.zoomSpeed()), 36, ZOOM_SPEED_Y - 4, 1, 1, 1, 1);
        canvas.textWithShadow(font, "GUI scale", 36, GUI_SCALE_Y + 5, 1, 1, 1, 1);
        renderDistance.render(canvas);
        fieldOfView.render(canvas);
        zoomFieldOfView.render(canvas);
        mouseSensitivity.render(canvas);
        zoomSpeed.render(canvas);
        decrease.render(canvas, font);
        increase.render(canvas, font);
        guiScale.render(canvas, font);
        back.render(canvas, font);
    }
}
