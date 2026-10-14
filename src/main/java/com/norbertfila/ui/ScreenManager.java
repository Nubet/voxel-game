package com.norbertfila.ui;

import java.util.ArrayDeque;
import java.util.Objects;

public final class ScreenManager {
    private Screen current;
    private boolean inputConsumed;
    private final ArrayDeque<Screen.Action> actions = new ArrayDeque<>();

    public void beginFrame() {
        inputConsumed = current != null;
    }

    public void open(Screen screen) {
        Objects.requireNonNull(screen);
        if (current != null) current.cancelInteraction();
        screen.cancelInteraction();
        current = screen;
        inputConsumed = true;
    }

    public void close() {
        if (current == null) return;
        current.cancelInteraction();
        current = null;
        // The closing click/key still belongs to the screen for the rest of this frame.
        inputConsumed = true;
    }

    public Screen current() {
        return current;
    }

    public boolean isOpen() {
        return current != null;
    }

    public boolean blocksGameplay() {
        return inputConsumed || isOpen();
    }

    public void updatePointer(boolean down, boolean pressed, double x, double y) {
        if (current != null) handle(current.updatePointer(down, pressed, x, y));
    }

    public void updatePointer(Screen.Pointer pointer) {
        if (current != null) handle(current.updatePointer(pointer));
    }

    public Screen.Action pollAction() {
        return actions.pollFirst();
    }

    public void keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (current != null)
            handle(current.keyPress(key == UiKey.SPACE && !current.acceptsText() ? UiKey.ACTIVATE : key));
    }

    public void input(UiEvent event) {
        Objects.requireNonNull(event);
        switch (event) {
            case UiEvent.Key key -> keyPress(key.key());
            case UiEvent.Character character -> {
                if (current != null && current.acceptsText()) current.textInput(character.codePoint());
            }
        }
    }

    public void scroll(double amount, double x, double y) {
        if (current != null) handle(current.scroll(amount, x, y));
    }

    public void cancelInteraction() {
        if (current != null) current.cancelInteraction();
    }

    public void render(UiCanvas canvas, UiViewport viewport) {
        if (current != null) current.render(canvas, viewport);
    }

    public void layout(UiViewport viewport) {
        if (current != null) current.layout(viewport);
    }

    private void handle(Screen.Action action) {
        if (action == Screen.Action.CLOSE) close();
        else if (action != Screen.Action.NONE) {
            if (actions.size() == 64) throw new IllegalStateException("UI action queue full");
            actions.addLast(Objects.requireNonNull(action));
        }
    }
}
