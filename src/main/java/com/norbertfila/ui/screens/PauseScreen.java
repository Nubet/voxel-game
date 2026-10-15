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

public final class PauseScreen implements Screen {
    private final Translations translations;
    private final Button options = new Button(new UiBounds(220, 196, 200, 24), "");
    public static final UiBounds RESUME_BOUNDS = new UiBounds(220, 132, 120, 24);
    private static final BitmapFont FONT = BitmapFont.DEFAULT;
    private final Button resume = new Button(RESUME_BOUNDS, "");
    private final Button menu = new Button(new UiBounds(220, 164, 200, 24), "");
    private boolean pointerReady = true;
    private int panelX = 160, panelY = 100;

    public PauseScreen() {
        this(new Translations(Language.ENGLISH));
    }

    public PauseScreen(Translations translations) {
        this.translations = Objects.requireNonNull(translations);
    }

    @Override
    public void layout(UiViewport viewport) {
        int nextPanelX = Math.max(0, ((int) Math.ceil(viewport.width()) - 320) / 2);
        int nextPanelY = Math.max(0, ((int) Math.ceil(viewport.height()) - 192) / 2);
        boolean moved = panelX != nextPanelX || panelY != nextPanelY;
        panelX = nextPanelX;
        panelY = nextPanelY;
        resume.setPosition(panelX + 100, panelY + 76);
        menu.setPosition(panelX + 60, panelY + 108);
        options.setPosition(panelX + 60, panelY + 140);
        if (moved) cancelInteraction();
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            resume.setFocused(resume.bounds().contains(x, y));
            menu.setFocused(menu.bounds().contains(x, y));
            options.setFocused(options.bounds().contains(x, y));
        }
        boolean close = resume.updatePointer(down, pressed, x, y);
        boolean leave = menu.updatePointer(down, pressed, x, y);
        if (options.updatePointer(down, pressed, x, y)) return Action.OPTIONS;
        return close ? Action.CLOSE : leave ? Action.BACK_TO_MENU : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        if (key == UiKey.BACK) return Action.CLOSE;
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int current = resume.focused() ? 0 : menu.focused() ? 1 : options.focused() ? 2 : -1;
            int next = Math.floorMod(current + (key == UiKey.NEXT ? 1 : -1), 3);
            resume.setFocused(next == 0);
            menu.setFocused(next == 1);
            options.setFocused(next == 2);
        }
        if (options.keyPress(key)) return Action.OPTIONS;
        if (resume.keyPress(key)) return Action.CLOSE;
        return menu.keyPress(key) ? Action.BACK_TO_MENU : Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        resume.cancelInteraction();
        menu.cancelInteraction();
        options.cancelInteraction();
        pointerReady = false;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        int width = (int) Math.ceil(viewport.width()), height = (int) Math.ceil(viewport.height());
        int x = panelX, y = panelY;
        canvas.rectangle(0, 0, width, height, 0, 0, 0, .55f);
        canvas.rectangle(x, y, 320, 192, .12f, .14f, .17f, 1);
        canvas.textWithShadow(FONT, translations.text("pause.title"), x + 12, y + 16, 1, 1, 1, 1);
        canvas.textWithShadow(FONT, translations.text("pause.hint"), x + 12, y + 48, 1, .75f, .78f, .82f, 1);
        resume.setLabel(translations.text("pause.resume"));
        menu.setLabel(translations.text("pause.menu"));
        options.setLabel(translations.text("action.options"));
        options.render(canvas, FONT);
        resume.render(canvas, FONT);
        menu.render(canvas, FONT);
    }

    public Button resumeButton() {
        return resume;
    }

    public Button menuButton() {
        return menu;
    }

    public Button optionsButton() {
        return options;
    }
}
