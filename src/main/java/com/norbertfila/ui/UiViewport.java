package com.norbertfila.ui;

public record UiViewport(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight, int guiScale) {
    public static final int MIN_GUI_WIDTH = 320;
    public static final int MIN_GUI_HEIGHT = 240;

    public UiViewport(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
        this(windowWidth, windowHeight, framebufferWidth, framebufferHeight, 0);
    }

    public UiViewport {
        if (windowWidth <= 0 || windowHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0)
            throw new IllegalArgumentException("UI viewport dimensions must be positive");
        if (guiScale < 0 || guiScale > 4) throw new IllegalArgumentException("GUI scale must be between 0 and 4");
    }

    public int scale() {
        int maximum = maximumScale();
        return guiScale == 0 ? maximum : Math.min(guiScale, maximum);
    }

    public int maximumScale() {
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
