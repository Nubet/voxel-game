package com.norbertfila.ui;

import java.util.Objects;

public final class Slider {
    private static final int KNOB_WIDTH = 12;
    private UiBounds track;
    private UiBounds bounds;
    private int minimum;
    private int maximum;
    private int step;
    private int value;
    private boolean focused;
    private boolean hovered;
    private boolean dragging;

    public Slider(UiBounds track, int minimum, int maximum, int step, int value) {
        this.track = Objects.requireNonNull(track);
        if (minimum >= maximum || step <= 0 || ((long) maximum - minimum) % step != 0)
            throw new IllegalArgumentException("Invalid slider range or step");
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        bounds = new UiBounds(
                Math.subtractExact(track.x(), KNOB_WIDTH / 2),
                Math.subtractExact(track.y(), KNOB_WIDTH / 2),
                Math.addExact(track.width(), KNOB_WIDTH),
                Math.addExact(track.height(), KNOB_WIDTH));
        setValue(value);
    }

    public UiBounds bounds() {
        return bounds;
    }

    public int value() {
        return value;
    }

    public boolean focused() {
        return focused;
    }

    public boolean hovered() {
        return hovered;
    }

    public boolean dragging() {
        return dragging;
    }

    public double fraction() {
        return (value - (long) minimum) / (double) (maximum - (long) minimum);
    }

    public void setValue(int value) {
        if (value < minimum || value > maximum || (value - (long) minimum) % step != 0)
            throw new IllegalArgumentException("Value does not lie on slider steps");
        this.value = value;
    }

    public void setRange(int minimum, int maximum, int step) {
        if (minimum >= maximum || step <= 0 || ((long) maximum - minimum) % step != 0)
            throw new IllegalArgumentException("Invalid slider range or step");
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        if (value < minimum || value > maximum || (value - (long) minimum) % step != 0)
            value = Math.max(minimum, Math.min(maximum, minimum + Math.round((value - minimum) / (float) step) * step));
        dragging = false;
    }

    public void setPosition(int x, int y) {
        track = new UiBounds(x, y, track.width(), track.height());
        bounds = new UiBounds(
                Math.subtractExact(x, KNOB_WIDTH / 2),
                Math.subtractExact(y, KNOB_WIDTH / 2),
                Math.addExact(track.width(), KNOB_WIDTH),
                Math.addExact(track.height(), KNOB_WIDTH));
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        if (!focused) dragging = false;
    }

    public void cancelInteraction() {
        focused = false;
        hovered = false;
        dragging = false;
    }

    public boolean updatePointer(boolean down, boolean press, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            cancelInteraction();
            return false;
        }
        hovered = bounds.contains(x, y);
        if (press) dragging = down && hovered;
        if (!dragging) return false;
        double fraction = Math.max(0, Math.min(1, (x - track.x()) / track.width()));
        long steps = ((long) maximum - minimum) / step;
        int next = Math.toIntExact(minimum + Math.round(fraction * steps) * step);
        boolean changed = next != value;
        value = next;
        if (!down) dragging = false;
        return changed;
    }

    public boolean keyPress(UiKey key) {
        if (!focused || (key != UiKey.DECREASE && key != UiKey.INCREASE)) return false;
        long delta = key == UiKey.DECREASE ? -(long) step : step;
        int next = (int) Math.max(minimum, Math.min(maximum, value + delta));
        boolean changed = next != value;
        value = next;
        return changed;
    }

    public void render(UiCanvas canvas) {
        if (focused)
            canvas.rectangle(bounds.x(), bounds.y() + bounds.height() + 1, bounds.width(), 1, .96f, .8f, .3f, 1);
        canvas.rectangle(track.x(), track.y(), track.width(), track.height(), .18f, .21f, .27f, 1);
        canvas.rectangle(track.x(), track.y(), (int) (track.width() * fraction()), track.height(), .22f, .67f, .78f, 1);
        int knobX = track.x() + (int) (track.width() * fraction());
        float shade = dragging ? 1 : hovered ? .95f : .85f;
        canvas.rectangle(
                knobX - KNOB_WIDTH / 2,
                track.y() - KNOB_WIDTH / 2,
                KNOB_WIDTH,
                track.height() + KNOB_WIDTH,
                shade,
                shade,
                shade,
                1);
    }
}
