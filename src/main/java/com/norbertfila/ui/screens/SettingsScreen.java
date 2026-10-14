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
    public static final int PANEL_WIDTH = 320;
    public static final int PANEL_HEIGHT = 160;
    public static final int SLIDER_X = 84;
    public static final int SLIDER_Y = 104;
    public static final int SLIDER_WIDTH = 200;
    public static final int SLIDER_HEIGHT = 16;
    public static final int BUTTON_Y = 102;
    public static final int BUTTON_SIZE = 20;
    public static final int MINUS_X = 56;
    public static final int PLUS_X = 292;
    public static final UiBounds BACK_BOUNDS = new UiBounds(124, 148, 120, 24);

    private final GameSettings settings;
    private final Button decrease = new Button(new UiBounds(MINUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE), "-");
    private final Button increase = new Button(new UiBounds(PLUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE), "+");
    private final Slider slider;
    private final Button back = new Button(BACK_BOUNDS, "Back");
    private int focusIndex = -1;
    private boolean pointerReady = true;

    public SettingsScreen(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        slider = new Slider(
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
        if (mousePressed) {
            if (decrease.enabled() && decrease.bounds().contains(cursorX, cursorY)) focus(0);
            else if (slider.bounds().contains(cursorX, cursorY)) focus(1);
            else if (increase.enabled() && increase.bounds().contains(cursorX, cursorY)) focus(2);
            else if (back.bounds().contains(cursorX, cursorY)) focus(3);
            else focus(-1);
        }
        boolean minus = decrease.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean plus = increase.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        slider.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean close = back.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        applyControls(minus, plus);
        return close ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (key == UiKey.BACK) return Action.CLOSE;
        syncControls();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int direction = key == UiKey.NEXT ? 1 : -1;
            int next = focusIndex < 0 ? (direction > 0 ? 3 : 0) : focusIndex;
            do {
                next = Math.floorMod(next + direction, 4);
            } while ((next == 0 && !decrease.enabled()) || (next == 2 && !increase.enabled()));
            focus(next);
            return Action.NONE;
        }
        boolean minus = decrease.keyPress(key), plus = increase.keyPress(key);
        slider.keyPress(key);
        applyControls(minus, plus);
        return back.keyPress(key) ? Action.CLOSE : Action.NONE;
    }

    private void applyControls(boolean minus, boolean plus) {
        settings.setRenderDistance(minus ? renderDistance() - 1 : plus ? renderDistance() + 1 : slider.value());
        syncControls();
    }

    @Override
    public void cancelInteraction() {
        focusIndex = -1;
        pointerReady = false;
        decrease.cancelInteraction();
        increase.cancelInteraction();
        slider.cancelInteraction();
        back.cancelInteraction();
    }

    private void focus(int index) {
        focusIndex = index;
        decrease.setFocused(index == 0);
        slider.setFocused(index == 1);
        increase.setFocused(index == 2);
        back.setFocused(index == 3);
    }

    private void syncControls() {
        slider.setValue(settings.renderDistance());
        decrease.setEnabled(canDecrease());
        increase.setEnabled(canIncrease());
    }

    public Button decreaseButton() {
        return decrease;
    }

    public Button increaseButton() {
        return increase;
    }

    public Slider slider() {
        return slider;
    }

    public Button backButton() {
        return back;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        syncControls();
        canvas.rectangle(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, .06f, .08f, .11f, .94f);
        slider.render(canvas);
        BitmapFont font = BitmapFont.DEFAULT;
        decrease.render(canvas, font);
        increase.render(canvas, font);
        back.render(canvas, font);
        canvas.textWithShadow(font, "Settings", PANEL_X + 12, PANEL_Y + 10, 1, 1, 1, 1);
        canvas.textWithShadow(
                font, "Render distance: " + renderDistance() + " chunks", PANEL_X + 12, PANEL_Y + 44, 1, 1, 1, 1);
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
}
