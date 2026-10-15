package com.norbertfila.ui.screens;

import com.norbertfila.i18n.*;
import com.norbertfila.ui.*;
import java.util.Objects;

public final class SaveErrorScreen implements Screen {
    private final Translations translations;
    private final Button retry = new Button(new UiBounds(60, 100, 200, 24), "");
    private final Button discard = new Button(new UiBounds(60, 132, 200, 24), "");
    private boolean pointerReady;

    public SaveErrorScreen() {
        this(new Translations(Language.ENGLISH));
    }

    public SaveErrorScreen(Translations translations) {
        this.translations = Objects.requireNonNull(translations);
        retry.setFocused(true);
    }

    @Override
    public void layout(UiViewport viewport) {
        int x = Math.max(0, ((int) viewport.width() - 200) / 2);
        int y = Math.max(48, (int) viewport.height() / 2 - 20);
        if (retry.bounds().x() != x || retry.bounds().y() != y) cancelInteraction();
        retry.setPosition(x, y);
        discard.setPosition(x, y + 32);
    }

    @Override
    public Action keyPress(UiKey key) {
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            boolean next = !retry.focused();
            retry.setFocused(next);
            discard.setFocused(!next);
        }
        if (retry.keyPress(key)) return Action.RETRY_SAVE;
        return discard.keyPress(key) ? Action.DISCARD_CHANGES : Action.NONE;
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            retry.setFocused(retry.bounds().contains(x, y));
            discard.setFocused(discard.bounds().contains(x, y));
        }
        boolean save = retry.updatePointer(down, pressed, x, y);
        boolean leave = discard.updatePointer(down, pressed, x, y);
        return save ? Action.RETRY_SAVE : leave ? Action.DISCARD_CHANGES : Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        retry.cancelInteraction();
        discard.cancelInteraction();
        retry.setFocused(true);
        discard.setFocused(false);
        pointerReady = false;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        canvas.textWithShadow(
                BitmapFont.DEFAULT,
                translations.text("save.failed"),
                retry.bounds().x(),
                retry.bounds().y() - 30,
                1,
                1,
                .5f,
                .5f,
                1);
        retry.setLabel(translations.text("save.retry"));
        discard.setLabel(translations.text("save.discard"));
        retry.render(canvas, BitmapFont.DEFAULT);
        discard.render(canvas, BitmapFont.DEFAULT);
    }
}
