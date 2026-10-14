package com.norbertfila.ui.screens;

import com.norbertfila.ui.*;
import java.util.Objects;
import java.util.OptionalLong;

public final class CreateWorldScreen implements Screen {
    private static final int WIDTH = 304, HEIGHT = 224;
    private final TextField name = new TextField(new UiBounds(12, 48, 280, 22), 64);
    private final TextField seed = new TextField(new UiBounds(12, 100, 280, 22), 20);
    private final Button create = new Button(new UiBounds(12, 192, 132, 20), "Utwórz");
    private final Button back = new Button(new UiBounds(160, 192, 132, 20), "Anuluj");
    private int x, y, focus;
    private boolean pointerReady, submitted;

    public CreateWorldScreen() {
        focus(0);
        sync();
    }

    @Override
    public void layout(UiViewport viewport) {
        int nx = Math.max(0, ((int) viewport.width() - WIDTH) / 2);
        int ny = Math.max(0, ((int) viewport.height() - HEIGHT) / 2);
        if (nx != x || ny != y) cancelInteraction();
        x = nx;
        y = ny;
        name.setPosition(x + 12, y + 48);
        seed.setPosition(x + 12, y + 100);
        create.setPosition(x + 12, y + 192);
        back.setPosition(x + 160, y + 192);
    }

    public TextField nameField() {
        return name;
    }

    public TextField seedField() {
        return seed;
    }

    public Button createButton() {
        sync();
        return create;
    }

    public Button backButton() {
        return back;
    }

    public String validationMessage() {
        if (name.text().strip().isEmpty()) return "Podaj nazwę świata";
        try {
            parsedSeed();
        } catch (NumberFormatException invalid) {
            return "Seed musi być liczbą long";
        }
        return "";
    }

    private OptionalLong parsedSeed() {
        String value = seed.text().strip();
        return value.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
    }

    private void sync() {
        create.setEnabled(!submitted && validationMessage().isEmpty());
    }

    private Action submit() {
        sync();
        if (!create.enabled()) return Action.NONE;
        submitted = true;
        sync();
        return new Action.CreateWorld(name.text(), parsedSeed());
    }

    @Override
    public boolean acceptsText() {
        return !submitted && (focus == 0 || focus == 1);
    }

    @Override
    public void textInput(int codePoint) {
        if (!acceptsText()) return;
        (focus == 0 ? name : seed).textInput(codePoint);
        sync();
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (submitted) return Action.NONE;
        if (key == UiKey.BACK) return Action.CLOSE;
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int next = focus;
            if (next < 0 && key == UiKey.PREVIOUS) next = 0;
            do next = Math.floorMod(next + (key == UiKey.NEXT ? 1 : -1), 4);
            while (next == 2 && !create.enabled());
            focus(next);
        } else if (key == UiKey.ACTIVATE) {
            if (focus == 3) return Action.CLOSE;
            return submit();
        } else if (focus == 0 || focus == 1) {
            (focus == 0 ? name : seed).keyPress(key);
            sync();
        }
        return Action.NONE;
    }

    private void focus(int index) {
        focus = index;
        name.setFocused(index == 0);
        seed.setFocused(index == 1);
        create.setFocused(index == 2);
        back.setFocused(index == 3);
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double px, double py) {
        if (submitted) return Action.NONE;
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            name.updatePointer(true, px, py);
            seed.updatePointer(true, px, py);
            focus(
                    name.focused()
                            ? 0
                            : seed.focused()
                                    ? 1
                                    : create.enabled() && create.bounds().contains(px, py)
                                            ? 2
                                            : back.bounds().contains(px, py) ? 3 : -1);
        }
        if (create.updatePointer(down, pressed, px, py)) return submit();
        return back.updatePointer(down, pressed, px, py) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        pointerReady = false;
        create.cancelInteraction();
        back.cancelInteraction();
        focus(0);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        sync();
        canvas.rectangle(x, y, WIDTH, HEIGHT, .06f, .08f, .11f, 1);
        var font = BitmapFont.DEFAULT;
        canvas.textWithShadow(font, "Nowy świat", x + 12, y + 8, 1, 1, 1, 1);
        canvas.text(font, "Nazwa świata (1-64)", x + 12, y + 30, .8f, .85f, .9f, 1);
        canvas.text(font, "Seed (opcjonalny)", x + 12, y + 82, .8f, .85f, .9f, 1);
        name.render(canvas);
        seed.render(canvas);
        canvas.text(font, "Tryb: Survival", x + 12, y + 134, .8f, .85f, .9f, 1);
        canvas.text(font, font.ellipsize(validationMessage(), 280), x + 12, y + 160, 1, .55f, .4f, 1);
        create.render(canvas, font);
        back.render(canvas, font);
    }
}
