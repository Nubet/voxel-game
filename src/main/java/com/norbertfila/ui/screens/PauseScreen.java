package com.norbertfila.ui.screens;

import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.Button;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.UiBounds;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;

public final class PauseScreen implements Screen {
    public static final UiBounds RESUME_BOUNDS = new UiBounds(220, 132, 120, 24);
    private static final BitmapFont FONT = BitmapFont.DEFAULT;
    private final Button resume = new Button(RESUME_BOUNDS, "Resume");
    private boolean pointerReady = true;

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed && !resume.bounds().contains(x, y)) resume.setFocused(false);
        boolean close = resume.updatePointer(down, pressed, x, y);
        return close ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        if (key == UiKey.BACK) return Action.CLOSE;
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) resume.setFocused(true);
        return resume.keyPress(key) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        resume.cancelInteraction();
        pointerReady = false;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        int width = (int) viewport.width(), height = (int) viewport.height();
        int x = Math.max(0, (width - 320) / 2), y = Math.max(0, (height - 160) / 2);
        canvas.rectangle(0, 0, width, height, 0, 0, 0, .55f);
        canvas.rectangle(x, y, 320, 160, .12f, .14f, .17f, 1);
        canvas.textWithShadow(FONT, "Paused", x + 12, y + 16, 1, 1, 1, 1);
        canvas.textWithShadow(FONT, "ESC: resume", x + 12, y + 48, 1, .75f, .78f, .82f, 1);
        resume.render(canvas, FONT);
    }

    public Button resumeButton() {
        return resume;
    }
}
