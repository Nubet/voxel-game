package com.norbertfila.ui.screens;

import com.norbertfila.i18n.*;
import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.Button;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.UiBounds;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import java.util.Objects;

public final class MainMenuScreen implements Screen {
    private final Translations translations;
    private final Button options = new Button(new UiBounds(60, 164, 200, 24), "");
    private final Button play = new Button(new UiBounds(60, 100, 200, 24), "");
    private final Button quit = new Button(new UiBounds(60, 132, 200, 24), "");
    private final boolean sessionFailed;
    private int focus;
    private boolean pointerReady;

    public MainMenuScreen(boolean sessionFailed) {
        this(sessionFailed, new Translations(Language.ENGLISH));
    }

    public MainMenuScreen(boolean sessionFailed, Translations translations) {
        this.translations = Objects.requireNonNull(translations);
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
        options.setPosition(x, y + 64);
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            if (play.bounds().contains(x, y)) focus(0);
            else if (quit.bounds().contains(x, y)) focus(1);
            else if (options.bounds().contains(x, y)) focus(2);
        }
        boolean start = play.updatePointer(down, pressed, x, y);
        boolean exit = quit.updatePointer(down, pressed, x, y);
        if (options.updatePointer(down, pressed, x, y)) return Action.OPTIONS;
        return start ? Action.PLAY : exit ? Action.QUIT : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        if (key == UiKey.BACK) return Action.QUIT;
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) focus(Math.floorMod(focus + (key == UiKey.NEXT ? 1 : -1), 3));
        if (options.keyPress(key)) return Action.OPTIONS;
        if (play.keyPress(key)) return Action.PLAY;
        return quit.keyPress(key) ? Action.QUIT : Action.NONE;
    }

    private void focus(int value) {
        focus = value;
        play.setFocused(value == 0);
        quit.setFocused(value == 1);
        options.setFocused(value == 2);
    }

    @Override
    public void cancelInteraction() {
        play.cancelInteraction();
        quit.cancelInteraction();
        options.cancelInteraction();
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
                translations.text("menu.title"),
                center - font.width(translations.text("menu.title")) / 2,
                play.bounds().y() - 44,
                1,
                1,
                1,
                1);
        play.setLabel(translations.text("action.play"));
        quit.setLabel(translations.text("action.quit"));
        options.setLabel(translations.text("action.options"));
        options.render(canvas, font);
        play.render(canvas, font);
        quit.render(canvas, font);
        if (sessionFailed) {
            canvas.textWithShadow(
                    font,
                    translations.text("menu.sessionFailed"),
                    center - font.width(translations.text("menu.sessionFailed")) / 2,
                    options.bounds().y() + 40,
                    1,
                    .55f,
                    .4f,
                    1);
            canvas.textWithShadow(
                    font,
                    translations.text("menu.seeLog"),
                    center - font.width(translations.text("menu.seeLog")) / 2,
                    options.bounds().y() + 56,
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

    public Button optionsButton() {
        return options;
    }
}
