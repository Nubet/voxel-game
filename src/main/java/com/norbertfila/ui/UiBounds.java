package com.norbertfila.ui;

public record UiBounds(int x, int y, int width, int height) {
    public UiBounds {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("UI bounds must have positive dimensions");
    }

    public boolean contains(double cursorX, double cursorY) {
        return Double.isFinite(cursorX)
                && Double.isFinite(cursorY)
                && cursorX >= x
                && cursorX < (double) x + width
                && cursorY >= y
                && cursorY < (double) y + height;
    }
}
