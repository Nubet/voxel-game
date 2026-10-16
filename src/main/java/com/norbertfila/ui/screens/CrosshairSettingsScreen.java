package com.norbertfila.ui.screens;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Translations;
import com.norbertfila.ui.*;
import com.norbertfila.ui.crosshair.*;
import java.util.Objects;

public final class CrosshairSettingsScreen implements Screen {
    private static final int WIDTH = 304, HEIGHT = 240;
    private static final CrosshairStyle[] PREVIEWS = {
        CrosshairStyle.DEFAULT, CrosshairStyle.BLOCK, CrosshairStyle.ENTITY, CrosshairStyle.RANGED
    };
    private final GameSettings settings;
    private final Translations translations;
    private final CrosshairRenderer renderer = new CrosshairRenderer();
    private final Button hide = new Button(new UiBounds(212, 52, 80, 20), "");
    private final Button smooth = new Button(new UiBounds(212, 78, 80, 20), "");
    private final Slider scale =
            new Slider(new UiBounds(148, 116, 136, 8), CrosshairSettings.MIN_SCALE, CrosshairSettings.MAX_SCALE, 1, 1);
    private final Slider opacity = new Slider(new UiBounds(148, 148, 136, 8), 0, 100, 1, 100);
    private final Button preview = new Button(new UiBounds(12, 208, 100, 20), "");
    private final Button back = new Button(new UiBounds(220, 208, 76, 20), "");
    private int x, y, focus = -1, previewIndex;
    private boolean pointerReady = true;

    public CrosshairSettingsScreen(GameSettings settings) {
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
            if (hide.bounds().contains(cursorX, cursorY)) focus(0);
            else if (smooth.bounds().contains(cursorX, cursorY)) focus(1);
            else if (scale.bounds().contains(cursorX, cursorY)) focus(2);
            else if (opacity.bounds().contains(cursorX, cursorY)) focus(3);
            else if (preview.bounds().contains(cursorX, cursorY)) focus(4);
            else if (back.bounds().contains(cursorX, cursorY)) focus(5);
            else focus(-1);
        }
        if (hide.updatePointer(down, pressed, cursorX, cursorY)) toggleHide();
        if (smooth.updatePointer(down, pressed, cursorX, cursorY)) toggleSmooth();
        boolean changed = scale.updatePointer(down, pressed, cursorX, cursorY);
        changed |= opacity.updatePointer(down, pressed, cursorX, cursorY);
        if (changed) applySliders();
        if (preview.updatePointer(down, pressed, cursorX, cursorY)) cyclePreview();
        return back.updatePointer(down, pressed, cursorX, cursorY) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (key == UiKey.BACK) return Action.CLOSE;
        sync();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int direction = key == UiKey.NEXT ? 1 : -1;
            int current = focus < 0 ? (direction > 0 ? -1 : 6) : focus;
            focus(Math.floorMod(current + direction, 6));
        } else {
            switch (focus) {
                case 0 -> {
                    if (hide.keyPress(key)) toggleHide();
                }
                case 1 -> {
                    if (smooth.keyPress(key)) toggleSmooth();
                }
                case 2 -> {
                    if (scale.keyPress(key)) applySliders();
                }
                case 3 -> {
                    if (opacity.keyPress(key)) applySliders();
                }
                case 4 -> {
                    if (preview.keyPress(key)) cyclePreview();
                }
                case 5 -> {
                    if (back.keyPress(key)) return Action.CLOSE;
                }
                default -> {}
            }
        }
        return Action.NONE;
    }

    private void toggleHide() {
        var c = settings.crosshair();
        settings.setCrosshair(
                new CrosshairSettings(c.dynamic(), !c.hideWithoutTarget(), c.smooth(), c.scale(), c.opacity()));
    }

    private void toggleSmooth() {
        var c = settings.crosshair();
        settings.setCrosshair(
                new CrosshairSettings(c.dynamic(), c.hideWithoutTarget(), !c.smooth(), c.scale(), c.opacity()));
    }

    private void applySliders() {
        var c = settings.crosshair();
        settings.setCrosshair(
                new CrosshairSettings(c.dynamic(), c.hideWithoutTarget(), c.smooth(), scale.value(), opacity.value()));
    }

    private void cyclePreview() {
        previewIndex = (previewIndex + 1) % PREVIEWS.length;
    }

    private void sync() {
        var c = settings.crosshair();
        scale.setValue(c.scale());
        opacity.setValue(c.opacity());
        hide.setLabel(translations.text(c.hideWithoutTarget() ? "crosshair.on" : "crosshair.off"));
        smooth.setLabel(translations.text(c.smooth() ? "crosshair.on" : "crosshair.off"));
        preview.setLabel(translations.text(
                "crosshair.style." + PREVIEWS[previewIndex].name().toLowerCase(java.util.Locale.ROOT)));
        back.setLabel(translations.text("action.back"));
    }

    private void focus(int index) {
        focus = index;
        hide.setFocused(index == 0);
        smooth.setFocused(index == 1);
        scale.setFocused(index == 2);
        opacity.setFocused(index == 3);
        preview.setFocused(index == 4);
        back.setFocused(index == 5);
    }

    @Override
    public void cancelInteraction() {
        focus = -1;
        pointerReady = false;
        hide.cancelInteraction();
        smooth.cancelInteraction();
        scale.cancelInteraction();
        opacity.cancelInteraction();
        preview.cancelInteraction();
        back.cancelInteraction();
    }

    @Override
    public void layout(UiViewport viewport) {
        int nextX = Math.max(0, ((int) viewport.width() - WIDTH) / 2);
        int nextY = Math.max(0, ((int) viewport.height() - HEIGHT) / 2);
        if (x != nextX || y != nextY) cancelInteraction();
        x = nextX;
        y = nextY;
        hide.setPosition(x + 212, y + 52);
        smooth.setPosition(x + 212, y + 78);
        scale.setPosition(x + 148, y + 116);
        opacity.setPosition(x + 148, y + 148);
        preview.setPosition(x + 12, y + 208);
        back.setPosition(x + 220, y + 208);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        sync();
        canvas.rectangle(x, y, WIDTH, HEIGHT, .06f, .08f, .11f, .94f);
        label(canvas, "crosshair.title", 8);
        label(canvas, "crosshair.hide", 58);
        label(canvas, "crosshair.smooth", 84);
        canvas.textWithShadow(
                BitmapFont.DEFAULT, translations.text("crosshair.scale", scale.value()), x + 12, y + 112, 1, 1, 1, 1);
        canvas.textWithShadow(
                BitmapFont.DEFAULT,
                translations.text("crosshair.opacity", opacity.value()),
                x + 12,
                y + 144,
                1,
                1,
                1,
                1);
        label(canvas, "crosshair.preview", 176);
        renderer.renderAt(canvas, x + 166, y + 200, PREVIEWS[previewIndex], settings.crosshair(), 1);
        hide.render(canvas, BitmapFont.DEFAULT);
        smooth.render(canvas, BitmapFont.DEFAULT);
        scale.render(canvas);
        opacity.render(canvas);
        preview.render(canvas, BitmapFont.DEFAULT);
        back.render(canvas, BitmapFont.DEFAULT);
    }

    private void label(UiCanvas canvas, String key, int offsetY) {
        canvas.textWithShadow(BitmapFont.DEFAULT, translations.text(key), x + 12, y + offsetY, 1, 1, 1, 1);
    }
}
