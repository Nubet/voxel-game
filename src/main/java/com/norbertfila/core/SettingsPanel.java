package com.norbertfila.core;

import java.util.Objects;

public final class SettingsPanel {
    public static final int PANEL_X = 24;
    public static final int PANEL_Y = 24;
    public static final int PANEL_WIDTH = 320;
    public static final int PANEL_HEIGHT = 160;
    public static final int SLIDER_X = 84;
    public static final int SLIDER_Y = 104;
    public static final int SLIDER_WIDTH = 200;
    public static final int SLIDER_HEIGHT = 16;
    public static final int BUTTON_Y = 102;
    public static final int BUTTON_SIZE = 20;
    public static final int MINUS_X = 56;
    public static final int PLUS_X = 292;

    private final GameSettings settings;
    private boolean open;

    public SettingsPanel(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public boolean open() {
        return open;
    }

    public void toggle() {
        open = !open;
    }

    public boolean update(boolean mouseDown, boolean mousePressed, double cursorX, double cursorY) {
        if (!open) return false;
        if (mousePressed && inside(cursorX, cursorY, MINUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            return settings.setRenderDistance(settings.renderDistance() - 1);
        }
        if (mousePressed && inside(cursorX, cursorY, PLUS_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            return settings.setRenderDistance(settings.renderDistance() + 1);
        }
        if (!mouseDown || !inside(cursorX, cursorY, SLIDER_X, SLIDER_Y, SLIDER_WIDTH, SLIDER_HEIGHT)) return false;
        double clamped = Math.max(SLIDER_X, Math.min(SLIDER_X + SLIDER_WIDTH, cursorX));
        double normalized = (clamped - SLIDER_X) / SLIDER_WIDTH;
        int value = GameSettings.MIN_RENDER_DISTANCE
                + (int) Math.round(normalized * (GameSettings.MAX_RENDER_DISTANCE - GameSettings.MIN_RENDER_DISTANCE));
        return settings.setRenderDistance(value);
    }

    public double sliderFraction() {
        return (settings.renderDistance() - GameSettings.MIN_RENDER_DISTANCE)
                / (double) (GameSettings.MAX_RENDER_DISTANCE - GameSettings.MIN_RENDER_DISTANCE);
    }

    public int renderDistance() {
        return settings.renderDistance();
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }
}
