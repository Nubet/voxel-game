package com.norbertfila.ui.screens;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.commands.GameCommands;
import com.norbertfila.ui.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ChatScreen implements Screen {
    private final ChatHistory history;
    private final Consumer<String> submit;
    private final GameCommands commands;
    private List<GameCommands.Completion> suggestions = List.of();
    private String observedText = "", completionInput = "";
    private int observedCaret = -1, selected;
    private boolean hidden, applied;
    private int scroll;
    private final TextField field = new TextField(new UiBounds(4, 4, 280, 22), ChatHistory.MAX_INPUT);

    public ChatScreen(ChatHistory history, String initial, Consumer<String> submit) {
        this(history, initial, submit, new GameCommands());
    }

    public ChatScreen(ChatHistory history, String initial, Consumer<String> submit, GameCommands commands) {
        this.history = Objects.requireNonNull(history);
        this.submit = Objects.requireNonNull(submit);
        this.commands = Objects.requireNonNull(commands);
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
        refreshSuggestions();
        int rowHeight = BitmapFont.LINE_HEIGHT + 4;
        String usage = commands.usage(field.text());
        int bottom = field.bounds().y() - 2;
        if (!usage.isEmpty() && bottom >= rowHeight) {
            bottom -= rowHeight;
            drawRow(canvas, usage, bottom, rowHeight, false);
        }
        var visible = suggestions();
        int count = Math.min(Math.min(6, visible.size()), Math.max(0, bottom / rowHeight));
        int first = Math.max(0, selected - count + 1);
        int y = bottom - count * rowHeight;
        for (int i = first; i < first + count; i++, y += rowHeight)
            drawRow(canvas, visible.get(i).text(), y, rowHeight, i == selected);
    }

    private void drawRow(UiCanvas canvas, String text, int y, int height, boolean active) {
        int width = field.bounds().width();
        canvas.rectangle(4, y, width, height, active ? .18f : .03f, active ? .25f : .04f, active ? .32f : .06f, .95f);
        String visible = text.substring(0, BitmapFont.DEFAULT.prefixLength(text, width - 8));
        canvas.text(BitmapFont.DEFAULT, visible, 8, y + 2, 1, 1, 1, 1);
    }

    public List<GameCommands.Completion> suggestions() {
        refreshSuggestions();
        return hidden ? List.of() : suggestions;
    }

    private void refreshSuggestions() {
        if (observedText.equals(field.text()) && observedCaret == field.caret()) return;
        observedText = completionInput = field.text();
        observedCaret = field.caret();
        suggestions = commands.suggest(observedText, observedCaret);
        selected = 0;
        hidden = false;
        applied = false;
    }

    private void complete(boolean previous) {
        if (suggestions().isEmpty()) return;
        if (applied || previous) selected = Math.floorMod(selected + (previous ? -1 : 1), suggestions.size());
        var suggestion = suggestions.get(selected);
        String replacement = completionInput.substring(0, suggestion.start())
                + suggestion.text()
                + completionInput.substring(suggestion.end());
        if (replacement.codePointCount(0, replacement.length()) > ChatHistory.MAX_INPUT) return;
        field.setText(completionInput);
        field.replaceRange(suggestion.start(), suggestion.end(), suggestion.text());
        observedText = field.text();
        observedCaret = field.caret();
        applied = true;
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
        refreshSuggestions();
        switch (key) {
            case BACK -> {
                if (!suggestions().isEmpty()) {
                    hidden = true;
                    return Action.NONE;
                }
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
            case NEXT -> complete(false);
            case PREVIOUS -> complete(true);
            case UP, DOWN -> {
                if (!suggestions().isEmpty()) {
                    selected = Math.floorMod(selected + (key == UiKey.UP ? -1 : 1), suggestions.size());
                    applied = false;
                } else if (key == UiKey.UP) field.setText(history.previous(field.text()));
                else field.setText(history.next());
            }
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
