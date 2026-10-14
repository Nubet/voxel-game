package com.norbertfila.ui;

public record UiViewport(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
    private static final int MIN_GUI_WIDTH = 480;
    private static final int MIN_GUI_HEIGHT = 270;

    public UiViewport {
        if (windowWidth <= 0 || windowHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0)
            throw new IllegalArgumentException("UI viewport dimensions must be positive");
    }

    public int scale() {
        return Math.max(1, Math.min(framebufferWidth / MIN_GUI_WIDTH, framebufferHeight / MIN_GUI_HEIGHT));
    }

    public double width() {
        return framebufferWidth / (double) scale();
    }

    public double height() {
        return framebufferHeight / (double) scale();
    }

    public double cursorX(double windowX) {
        if (!Double.isFinite(windowX)) throw new IllegalArgumentException("Cursor X must be finite");
        return windowX / windowWidth * width();
    }

    public double cursorY(double windowY) {
        if (!Double.isFinite(windowY)) throw new IllegalArgumentException("Cursor Y must be finite");
        return windowY / windowHeight * height();
    }
}
