package com.norbertfila.ui.screens;

import com.norbertfila.ui.*;
import java.util.Objects;

public final class WorldErrorScreen implements Screen {
    private final String title, detail;
    private final Button back = new Button(new UiBounds(60, 160, 200, 24), "Wstecz");
    private boolean pointerReady;

    public WorldErrorScreen(String title, String detail) {
        this.title = Objects.requireNonNull(title);
        this.detail = Objects.requireNonNull(detail);
        cancelInteraction();
    }

    @Override
    public void layout(UiViewport viewport) {
        int x = Math.max(0, ((int) viewport.width() - 200) / 2);
        int y = Math.max(100, (int) viewport.height() / 2 + 24);
        if (back.bounds().x() != x || back.bounds().y() != y) cancelInteraction();
        back.setPosition(x, y);
    }

    @Override
    public Action keyPress(UiKey key) {
        return key == UiKey.BACK || back.keyPress(key) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (!down) pointerReady = true;
        return pointerReady && back.updatePointer(down, pressed, x, y) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        back.cancelInteraction();
        back.setFocused(true);
        pointerReady = false;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        var font = BitmapFont.DEFAULT;
        int width = Math.min(300, (int) viewport.width() - 16);
        int x = Math.max(8, ((int) viewport.width() - width) / 2);
        canvas.textWithShadow(
                font, font.ellipsize(title, width), x, back.bounds().y() - 64, 1, .55f, .4f, 1);
        canvas.text(
                font,
                font.ellipsize(detail.replace('\n', ' ').replace('\r', ' '), width),
                x,
                back.bounds().y() - 40,
                .8f,
                .85f,
                .9f,
                1);
        back.render(canvas, font);
    }
}
