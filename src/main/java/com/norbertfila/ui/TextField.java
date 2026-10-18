package com.norbertfila.ui;

import java.util.Objects;

public final class TextField {
    private UiBounds bounds;
    private final int maximumCodePoints;
    private String text = "";
    private int caret, first;
    private boolean focused;

    public TextField(UiBounds bounds, int maximumCodePoints) {
        this.bounds = Objects.requireNonNull(bounds);
        if (bounds.width() < 12 || bounds.height() < BitmapFont.GLYPH_HEIGHT + 4 || maximumCodePoints < 1)
            throw new IllegalArgumentException("Invalid text field dimensions or limit");
        this.maximumCodePoints = maximumCodePoints;
    }

    public String text() {
        return text;
    }

    public int caret() {
        return caret;
    }

    public void setText(String value) {
        Objects.requireNonNull(value);
        if (value.codePointCount(0, value.length()) > maximumCodePoints
                || value.codePoints()
                        .anyMatch(cp -> Character.isISOControl(cp)
                                || cp >= 0xD800 && cp <= 0xDFFF
                                || Character.getType(cp) == Character.LINE_SEPARATOR
                                || Character.getType(cp) == Character.PARAGRAPH_SEPARATOR))
            throw new IllegalArgumentException("Invalid field text");
        text = value;
        caret = value.length();
        first = 0;
    }

    public void setWidth(int width) {
        if (width < 12) throw new IllegalArgumentException("Invalid field width");
        bounds = new UiBounds(bounds.x(), bounds.y(), width, bounds.height());
    }

    public void replaceRange(int start, int end, String inserted) {
        Objects.requireNonNull(inserted);
        if (start < 0
                || end < start
                || end > text.length()
                || start > 0 && start < text.length() && Character.isLowSurrogate(text.charAt(start))
                || end > 0 && end < text.length() && Character.isLowSurrogate(text.charAt(end)))
            throw new IllegalArgumentException("Invalid replacement range");
        setText(text.substring(0, start) + inserted + text.substring(end));
        caret = start + inserted.length();
    }

    public UiBounds bounds() {
        return bounds;
    }

    public boolean focused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public void setPosition(int x, int y) {
        bounds = new UiBounds(x, y, bounds.width(), bounds.height());
    }

    public void textInput(int codePoint) {
        if (!focused
                || !Character.isValidCodePoint(codePoint)
                || Character.isISOControl(codePoint)
                || codePoint >= 0xD800 && codePoint <= 0xDFFF
                || Character.getType(codePoint) == Character.LINE_SEPARATOR
                || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR
                || text.codePointCount(0, text.length()) >= maximumCodePoints) return;
        replace(caret, caret, new String(Character.toChars(codePoint)));
    }

    public void keyPress(UiKey key) {
        if (!focused) return;
        switch (Objects.requireNonNull(key)) {
            case HOME -> caret = 0;
            case END -> caret = text.length();
            case DECREASE -> {
                if (caret > 0) caret = text.offsetByCodePoints(caret, -1);
            }
            case INCREASE -> {
                if (caret < text.length()) caret = text.offsetByCodePoints(caret, 1);
            }
            case BACKSPACE -> {
                if (caret > 0) {
                    int previous = text.offsetByCodePoints(caret, -1);
                    replace(previous, caret, "");
                }
            }
            case DELETE -> {
                if (caret < text.length()) replace(caret, text.offsetByCodePoints(caret, 1), "");
            }
            default -> {}
        }
    }

    private void replace(int start, int end, String inserted) {
        if (first > start) first = first < end ? start : first + inserted.length() - (end - start);
        text = text.substring(0, start) + inserted + text.substring(end);
        caret = start + inserted.length();
    }

    public void updatePointer(boolean pressed, double x, double y) {
        if (!pressed) return;
        focused = bounds.contains(x, y);
        if (!focused) return;
        updateFirst();
        caret = first;
        int position = bounds.x() + 4;
        while (caret < text.length()) {
            int cp = text.codePointAt(caret), advance = BitmapFont.DEFAULT.advance(cp);
            if (x < position + advance / 2.0) break;
            caret += Character.charCount(cp);
            position += advance;
        }
    }

    private void updateFirst() {
        first = Math.min(first, caret);
        while (first < caret && BitmapFont.DEFAULT.width(text.substring(first, caret)) > bounds.width() - 10)
            first = text.offsetByCodePoints(first, 1);
    }

    public void render(UiCanvas canvas) {
        updateFirst();
        canvas.rectangle(
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                focused ? .96f : .3f,
                focused ? .8f : .35f,
                .3f,
                1);
        canvas.rectangle(bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, bounds.height() - 2, .04f, .05f, .07f, 1);
        String tail = text.substring(first);
        String visible = tail.substring(0, BitmapFont.DEFAULT.prefixLength(tail, bounds.width() - 8));
        int y = bounds.y() + (bounds.height() - BitmapFont.GLYPH_HEIGHT) / 2;
        canvas.text(BitmapFont.DEFAULT, visible, bounds.x() + 4, y, 1, 1, 1, 1);
        if (focused)
            canvas.rectangle(
                    bounds.x() + 4 + BitmapFont.DEFAULT.width(text.substring(first, caret)),
                    y,
                    1,
                    BitmapFont.GLYPH_HEIGHT,
                    1,
                    1,
                    1,
                    1);
    }
}
