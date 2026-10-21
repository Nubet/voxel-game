package com.norbertfila.ui.screens;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Translations;
import com.norbertfila.ui.*;
import java.util.Locale;
import java.util.Objects;

public final class DisplayScreen implements Screen {
    private static final int WIDTH = 304, HEIGHT = 200;
    private final GameSettings settings;
    private final Translations translations;
    private final Slider brightness = new Slider(
            new UiBounds(12, 44, 280, 12),
            GameSettings.MIN_BRIGHTNESS,
            GameSettings.MAX_BRIGHTNESS,
            1,
            GameSettings.DEFAULT_BRIGHTNESS);
    private final Slider contrast = new Slider(
            new UiBounds(12, 84, 280, 12),
            GameSettings.MIN_CONTRAST,
            GameSettings.MAX_CONTRAST,
            1,
            GameSettings.DEFAULT_CONTRAST);
    private final Slider gamma = new Slider(
            new UiBounds(12, 124, 280, 12),
            GameSettings.MIN_GAMMA,
            GameSettings.MAX_GAMMA,
            5,
            GameSettings.DEFAULT_GAMMA);
    private final Button reset = new Button(new UiBounds(12, 172, 120, 20), "");
    private final Button back = new Button(new UiBounds(220, 172, 76, 20), "");
    private int x, y, focus = -1;
    private boolean pointerReady = true;

    public DisplayScreen(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        translations = settings.translations();
        sync();
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double cursorX, double cursorY) {
        sync();
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            if (brightness.bounds().contains(cursorX, cursorY)) focus(0);
            else if (contrast.bounds().contains(cursorX, cursorY)) focus(1);
            else if (gamma.bounds().contains(cursorX, cursorY)) focus(2);
            else if (reset.bounds().contains(cursorX, cursorY)) focus(3);
            else if (back.bounds().contains(cursorX, cursorY)) focus(4);
            else focus(-1);
        }
        if (brightness.updatePointer(down, pressed, cursorX, cursorY)) settings.setBrightness(brightness.value());
        if (contrast.updatePointer(down, pressed, cursorX, cursorY)) settings.setContrast(contrast.value());
        if (gamma.updatePointer(down, pressed, cursorX, cursorY)) settings.setGamma(gamma.value());
        if (reset.updatePointer(down, pressed, cursorX, cursorY)) resetDefaults();
        return back.updatePointer(down, pressed, cursorX, cursorY) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (key == UiKey.BACK) return Action.CLOSE;
        sync();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int direction = key == UiKey.NEXT ? 1 : -1;
            int current = focus < 0 ? (direction > 0 ? -1 : 5) : focus;
            focus(Math.floorMod(current + direction, 5));
        } else {
            switch (focus) {
                case 0 -> {
                    if (brightness.keyPress(key)) settings.setBrightness(brightness.value());
                }
                case 1 -> {
                    if (contrast.keyPress(key)) settings.setContrast(contrast.value());
                }
                case 2 -> {
                    if (gamma.keyPress(key)) settings.setGamma(gamma.value());
                }
                case 3 -> {
                    if (reset.keyPress(key)) resetDefaults();
                }
                case 4 -> {
                    if (back.keyPress(key)) return Action.CLOSE;
                }
                default -> {}
            }
        }
        return Action.NONE;
    }

    private void resetDefaults() {
        settings.resetDisplay();
        sync();
    }

    private void sync() {
        brightness.setValue(settings.brightness());
        contrast.setValue(settings.contrast());
        gamma.setValue(settings.gamma());
        reset.setLabel(translations.text("display.reset"));
        back.setLabel(translations.text("action.back"));
    }

    private void focus(int index) {
        focus = index;
        brightness.setFocused(index == 0);
        contrast.setFocused(index == 1);
        gamma.setFocused(index == 2);
        reset.setFocused(index == 3);
        back.setFocused(index == 4);
    }

    @Override
    public void cancelInteraction() {
        focus = -1;
        pointerReady = false;
        brightness.cancelInteraction();
        contrast.cancelInteraction();
        gamma.cancelInteraction();
        reset.cancelInteraction();
        back.cancelInteraction();
    }

    @Override
    public void layout(UiViewport viewport) {
        int nextX = Math.max(0, ((int) Math.ceil(viewport.width()) - WIDTH) / 2);
        int nextY = Math.max(0, ((int) Math.ceil(viewport.height()) - HEIGHT) / 2);
        if (x != nextX || y != nextY) cancelInteraction();
        x = nextX;
        y = nextY;
        brightness.setPosition(x + 12, y + 44);
        contrast.setPosition(x + 12, y + 84);
        gamma.setPosition(x + 12, y + 124);
        reset.setPosition(x + 12, y + 172);
        back.setPosition(x + 220, y + 172);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        sync();
        canvas.rectangle(x, y, WIDTH, HEIGHT, .06f, .08f, .11f, .94f);
        text(canvas, translations.text("display.title"), 8, false);
        text(canvas, translations.text("display.brightness", settings.brightness()), 32, false);
        text(canvas, translations.text("display.contrast", settings.contrast()), 72, false);
        text(
                canvas,
                translations.text("display.gamma", String.format(Locale.ROOT, "%.2f", settings.gamma() / 100f)),
                112,
                false);
        text(canvas, translations.text("display.help"), 148, true);
        brightness.render(canvas);
        contrast.render(canvas);
        gamma.render(canvas);
        reset.render(canvas, BitmapFont.DEFAULT);
        back.render(canvas, BitmapFont.DEFAULT);
    }

    private void text(UiCanvas canvas, String value, int offsetY, boolean muted) {
        float color = muted ? .72f : 1;
        canvas.textWithShadow(BitmapFont.DEFAULT, value, x + 12, y + offsetY, color, color, color, 1);
    }

    public Slider brightnessSlider() {
        return brightness;
    }

    public Slider contrastSlider() {
        return contrast;
    }

    public Slider gammaSlider() {
        return gamma;
    }

    public Button resetButton() {
        return reset;
    }

    public Button backButton() {
        return back;
    }
}
