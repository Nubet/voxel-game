package com.norbertfila.ui;

import java.util.Objects;

public final class Button {
    private UiBounds bounds;
    private String label;
    private boolean enabled = true;
    private boolean hovered;
    private boolean focused;
    private boolean armed;
    private boolean keyPressed;

    public Button(UiBounds bounds, String label) {
        this.bounds = Objects.requireNonNull(bounds);
        this.label = Objects.requireNonNull(label);
    }

    public UiBounds bounds() {
        return bounds;
    }

    public void setLabel(String label) {
        this.label = Objects.requireNonNull(label);
    }

    public void setPosition(int x, int y) {
        bounds = new UiBounds(x, y, bounds.width(), bounds.height());
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean hovered() {
        return hovered;
    }

    public boolean focused() {
        return focused;
    }

    public boolean pressed() {
        return (armed && hovered) || keyPressed;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) cancelInteraction();
    }

    public void setFocused(boolean focused) {
        this.focused = focused && enabled;
        if (!this.focused) {
            armed = false;
            keyPressed = false;
        }
    }

    public void cancelInteraction() {
        hovered = false;
        focused = false;
        armed = false;
        keyPressed = false;
    }

    public boolean updatePointer(boolean down, boolean press, double x, double y) {
        keyPressed = false;
        hovered = enabled && bounds.contains(x, y);
        if (!enabled) return false;
        if (press) armed = down && hovered;
        if (down) return false;
        boolean activated = armed && hovered;
        armed = false;
        return activated;
    }

    public boolean keyPress(UiKey key) {
        if (!enabled || !focused || key != UiKey.ACTIVATE) return false;
        keyPressed = true;
        return true;
    }

    public void render(UiCanvas canvas, BitmapFont font) {
        if (focused)
            canvas.rectangle(
                    bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2, .96f, .8f, .3f, 1);
        float r = !enabled ? .07f : pressed() ? .1f : hovered ? .25f : .15f;
        float g = !enabled ? .08f : pressed() ? .12f : hovered ? .29f : .18f;
        float b = !enabled ? .1f : pressed() ? .16f : hovered ? .35f : .23f;
        canvas.rectangle(bounds.x(), bounds.y(), bounds.width(), bounds.height(), r, g, b, 1);
        float text = enabled ? 1 : .3f;
        canvas.textWithShadow(
                font,
                label,
                bounds.x() + (bounds.width() - font.width(label)) / 2,
                bounds.y() + (bounds.height() - BitmapFont.GLYPH_HEIGHT) / 2,
                text,
                text,
                text,
                1);
    }
}
