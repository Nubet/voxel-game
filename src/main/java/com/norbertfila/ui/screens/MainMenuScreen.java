package com.norbertfila.ui.screens;

import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.Button;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.UiBounds;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;

public final class MainMenuScreen implements Screen {
    private final Button play = new Button(new UiBounds(60, 100, 200, 24), "Play");
    private final Button quit = new Button(new UiBounds(60, 132, 200, 24), "Quit");
    private final boolean sessionFailed;
    private int focus;
    private boolean pointerReady;

    public MainMenuScreen(boolean sessionFailed) {
        this.sessionFailed = sessionFailed;
        play.setFocused(true);
    }

    @Override
    public void layout(UiViewport viewport) {
        int x = Math.max(0, ((int) viewport.width() - 200) / 2);
        int y = Math.max(48, (int) viewport.height() / 2 - 20);
        if (play.bounds().x() != x || play.bounds().y() != y) cancelInteraction();
        play.setPosition(x, y);
        quit.setPosition(x, y + 32);
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            if (play.bounds().contains(x, y)) focus(0);
            else if (quit.bounds().contains(x, y)) focus(1);
        }
        boolean start = play.updatePointer(down, pressed, x, y);
        boolean exit = quit.updatePointer(down, pressed, x, y);
        return start ? Action.PLAY : exit ? Action.QUIT : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        if (key == UiKey.BACK) return Action.QUIT;
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) focus(1 - focus);
        if (play.keyPress(key)) return Action.PLAY;
        return quit.keyPress(key) ? Action.QUIT : Action.NONE;
    }

    private void focus(int value) {
        focus = value;
        play.setFocused(value == 0);
        quit.setFocused(value == 1);
    }

    @Override
    public void cancelInteraction() {
        play.cancelInteraction();
        quit.cancelInteraction();
        pointerReady = false;
        focus(0);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        var font = BitmapFont.DEFAULT;
        canvas.rectangle(
                0, 0, (int) Math.ceil(viewport.width()), (int) Math.ceil(viewport.height()), .045f, .06f, .085f, 1);
        int center = (int) viewport.width() / 2;
        canvas.textWithShadow(
                font,
                "Voxel Game",
                center - font.width("Voxel Game") / 2,
                play.bounds().y() - 44,
                1,
                1,
                1,
                1);
        play.render(canvas, font);
        quit.render(canvas, font);
        if (sessionFailed) {
            canvas.textWithShadow(
                    font,
                    "Session failed.",
                    center - font.width("Session failed.") / 2,
                    quit.bounds().y() + 40,
                    1,
                    .55f,
                    .4f,
                    1);
            canvas.textWithShadow(
                    font,
                    "See log for details.",
                    center - font.width("See log for details.") / 2,
                    quit.bounds().y() + 56,
                    1,
                    .75f,
                    .65f,
                    1);
        }
    }

    public Button playButton() {
        return play;
    }

    public Button quitButton() {
        return quit;
    }
}
