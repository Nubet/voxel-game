package com.norbertfila.ui.screens;

import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import java.util.Objects;

public final class SessionStatusScreen implements Screen {
    private final String message;

    public SessionStatusScreen(String message) {
        this.message = Objects.requireNonNull(message);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        canvas.rectangle(
                0, 0, (int) Math.ceil(viewport.width()), (int) Math.ceil(viewport.height()), .045f, .06f, .085f, 1);
        var font = BitmapFont.DEFAULT;
        canvas.textWithShadow(
                font,
                message,
                ((int) viewport.width() - font.width(message)) / 2,
                (int) viewport.height() / 2 - 7,
                1,
                1,
                1,
                1);
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        return Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        return Action.NONE;
    }

    @Override
    public void cancelInteraction() {}
}
