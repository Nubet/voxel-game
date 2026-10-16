package com.norbertfila.ui.screens;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.ui.*;
import java.util.Objects;
import java.util.function.Consumer;

public final class ChatScreen implements Screen {
    private final ChatHistory history;
    private final Consumer<String> submit;
    private int scroll;
    private final TextField field = new TextField(new UiBounds(4, 4, 280, 22), ChatHistory.MAX_INPUT);

    public ChatScreen(ChatHistory history, String initial, Consumer<String> submit) {
        this.history = Objects.requireNonNull(history);
        this.submit = Objects.requireNonNull(submit);
        history.beginInput();
        field.setText(initial);
        field.setFocused(true);
    }

    public static String initialText(int codePoint) {
        return switch (codePoint) {
            case 't', 'T' -> "";
            case '/' -> "/";
            default -> null;
        };
    }

    public TextField field() {
        return field;
    }

    public int scrollOffset() {
        return scroll;
    }

    public void setScrollOffset(int value) {
        scroll = Math.max(0, value);
    }

    @Override
    public Action scroll(double amount, double x, double y) {
        scroll = (int) Math.clamp(scroll + amount * 3, 0, 51200);
        return Action.NONE;
    }

    @Override
    public void layout(UiViewport viewport) {
        field.setWidth(Math.max(12, (int) viewport.width() - 8));
        field.setPosition(4, Math.max(0, (int) viewport.height() - 26));
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        field.render(canvas);
    }

    @Override
    public boolean acceptsText() {
        return true;
    }

    @Override
    public void textInput(int codePoint) {
        field.textInput(codePoint);
    }

    @Override
    public Action requestClose() {
        return Action.CLOSE;
    }

    @Override
    public Action keyPress(UiKey key) {
        switch (key) {
            case BACK -> {
                return Action.CLOSE;
            }
            case ACTIVATE -> {
                String text = field.text().strip();
                if (!text.isEmpty()) {
                    history.remember(text);
                    submit.accept(text);
                }
                return Action.CLOSE;
            }
            case UP -> field.setText(history.previous(field.text()));
            case DOWN -> field.setText(history.next());
            default -> field.keyPress(key);
        }
        return Action.NONE;
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (pressed && field.bounds().contains(x, y)) field.updatePointer(true, x, y);
        return Action.NONE;
    }

    @Override
    public void cancelInteraction() {}
}
