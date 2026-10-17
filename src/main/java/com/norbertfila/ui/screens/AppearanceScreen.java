package com.norbertfila.ui.screens;

import com.norbertfila.assets.SkinLoader;
import com.norbertfila.core.GameSettings;
import com.norbertfila.player.visual.model.ModelVariant;
import com.norbertfila.render.SkinManager;
import com.norbertfila.ui.*;
import java.util.Locale;
import java.util.Objects;

public final class AppearanceScreen implements Screen {
    private static final int WIDTH = 304, HEIGHT = 240;
    private final GameSettings settings;
    private final SkinManager skins;
    private final Button variant = new Button(new UiBounds(12, 32, 280, 20), "");
    private final Button reload = new Button(new UiBounds(12, 64, 280, 20), "");
    private final Button back = new Button(new UiBounds(220, 208, 76, 20), "");
    private int x, y, focus = -1;
    private boolean pointerReady = true;

    public AppearanceScreen(GameSettings settings, SkinManager skins) {
        this.settings = Objects.requireNonNull(settings);
        this.skins = skins;
        reload.setEnabled(skins != null);
        sync();
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double cursorX, double cursorY) {
        sync();
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            if (variant.bounds().contains(cursorX, cursorY)) focus(0);
            else if (reload.bounds().contains(cursorX, cursorY)) focus(1);
            else if (back.bounds().contains(cursorX, cursorY)) focus(2);
            else focus(-1);
        }
        if (variant.updatePointer(down, pressed, cursorX, cursorY)) cycleVariant();
        if (reload.updatePointer(down, pressed, cursorX, cursorY)) skins.reload();
        return back.updatePointer(down, pressed, cursorX, cursorY) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (key == UiKey.BACK) return Action.CLOSE;
        sync();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int direction = key == UiKey.NEXT ? 1 : -1;
            int current = focus < 0 ? (direction > 0 ? -1 : 3) : focus;
            focus(Math.floorMod(current + direction, 3));
        } else {
            switch (focus) {
                case 0 -> {
                    if (variant.keyPress(key)) cycleVariant();
                }
                case 1 -> {
                    if (reload.keyPress(key)) skins.reload();
                }
                case 2 -> {
                    if (back.keyPress(key)) return Action.CLOSE;
                }
                default -> {}
            }
        }
        return Action.NONE;
    }

    private void cycleVariant() {
        settings.setModelVariant(
                settings.modelVariant() == ModelVariant.CLASSIC ? ModelVariant.SLIM : ModelVariant.CLASSIC);
        sync();
    }

    private void sync() {
        var translations = settings.translations();
        variant.setLabel(translations.text(
                "appearance.variant",
                translations.text(
                        "appearance.variant." + settings.modelVariant().name().toLowerCase(Locale.ROOT))));
        reload.setLabel(translations.text("appearance.reload"));
        back.setLabel(translations.text("action.back"));
    }

    private void focus(int index) {
        focus = index;
        variant.setFocused(index == 0);
        reload.setFocused(index == 1);
        back.setFocused(index == 2);
    }

    @Override
    public void cancelInteraction() {
        focus = -1;
        pointerReady = false;
        variant.cancelInteraction();
        reload.cancelInteraction();
        back.cancelInteraction();
    }

    @Override
    public void layout(UiViewport viewport) {
        int nextX = Math.max(0, ((int) viewport.width() - WIDTH) / 2);
        int nextY = Math.max(0, ((int) viewport.height() - HEIGHT) / 2);
        if (x != nextX || y != nextY) cancelInteraction();
        x = nextX;
        y = nextY;
        variant.setPosition(x + 12, y + 32);
        reload.setPosition(x + 12, y + 64);
        back.setPosition(x + 220, y + 208);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        sync();
        var translations = settings.translations();
        canvas.rectangle(x, y, WIDTH, HEIGHT, .06f, .08f, .11f, .94f);
        text(canvas, translations.text("appearance.title"), 8);
        text(canvas, SkinLoader.EXTERNAL_PATH.toString(), 96);
        if (skins != null) {
            var status = skins.status();
            text(
                    canvas,
                    translations.text(
                            "appearance.status." + status.state().name().toLowerCase(Locale.ROOT)),
                    114);
            text(canvas, status.source(), 132);
            if (!status.diagnostic().isEmpty()) text(canvas, translations.text("appearance.diagnostic"), 150);
        }
        text(canvas, translations.text("appearance.metadata"), 176);
        variant.render(canvas, BitmapFont.DEFAULT);
        reload.render(canvas, BitmapFont.DEFAULT);
        back.render(canvas, BitmapFont.DEFAULT);
    }

    private void text(UiCanvas canvas, String text, int offsetY) {
        canvas.textWithShadow(BitmapFont.DEFAULT, text, x + 12, y + offsetY, 1, 1, 1, 1);
    }
}
