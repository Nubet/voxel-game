package com.norbertfila.core;

import com.norbertfila.ui.Button;
import com.norbertfila.ui.Slider;
import com.norbertfila.ui.UiBounds;
import com.norbertfila.ui.UiKey;
import java.util.Objects;

public final class SettingsPanel {
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

    private final GameSettings settings;
    private final Button decrease = new Button(new UiBounds(MINUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE), "-");
    private final Button increase = new Button(new UiBounds(PLUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE), "+");
    private final Slider slider;
    private boolean open;
    private int focusIndex = -1;
    private boolean pointerReady = true;

    public SettingsPanel(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        slider = new Slider(
                new UiBounds(SLIDER_X, SLIDER_Y, SLIDER_WIDTH, SLIDER_HEIGHT),
                GameSettings.MIN_RENDER_DISTANCE,
                GameSettings.MAX_RENDER_DISTANCE,
                1,
                settings.renderDistance());
        syncControls();
    }

    public boolean open() {
        return open;
    }

    public void toggle() {
        open = !open;
        cancelInteraction();
        pointerReady = true;
    }

    public boolean update(boolean mouseDown, boolean mousePressed, double cursorX, double cursorY) {
        if (!open) return false;
        syncControls();
        if (!mouseDown) pointerReady = true;
        if (!pointerReady) return false;
        if (mousePressed) {
            if (decrease.enabled() && decrease.bounds().contains(cursorX, cursorY)) focus(0);
            else if (slider.bounds().contains(cursorX, cursorY)) focus(1);
            else if (increase.enabled() && increase.bounds().contains(cursorX, cursorY)) focus(2);
            else focus(-1);
        }
        boolean minus = decrease.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        boolean plus = increase.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        slider.updatePointer(mouseDown, mousePressed, cursorX, cursorY);
        return applyControls(minus, plus);
    }

    public boolean keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (!open) return false;
        syncControls();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int direction = key == UiKey.NEXT ? 1 : -1;
            int next = focusIndex < 0 ? (direction > 0 ? 2 : 0) : focusIndex;
            do {
                next = Math.floorMod(next + direction, 3);
            } while ((next == 0 && !decrease.enabled()) || (next == 2 && !increase.enabled()));
            focus(next);
            return false;
        }
        boolean minus = decrease.keyPress(key), plus = increase.keyPress(key);
        slider.keyPress(key);
        return applyControls(minus, plus);
    }

    private boolean applyControls(boolean minus, boolean plus) {
        boolean changed =
                settings.setRenderDistance(minus ? renderDistance() - 1 : plus ? renderDistance() + 1 : slider.value());
        syncControls();
        return changed;
    }

    public void cancelInteraction() {
        focusIndex = -1;
        pointerReady = false;
        decrease.cancelInteraction();
        increase.cancelInteraction();
        slider.cancelInteraction();
    }

    private void focus(int index) {
        focusIndex = index;
        decrease.setFocused(index == 0);
        slider.setFocused(index == 1);
        increase.setFocused(index == 2);
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
