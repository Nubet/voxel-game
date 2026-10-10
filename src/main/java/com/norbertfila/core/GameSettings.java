package com.norbertfila.core;

public final class GameSettings {
    public static final int MIN_RENDER_DISTANCE = 2;
    public static final int MAX_RENDER_DISTANCE = 16;

    private int renderDistance = 4;

    public int renderDistance() {
        return renderDistance;
    }

    public boolean setRenderDistance(int value) {
        if (value < MIN_RENDER_DISTANCE || value > MAX_RENDER_DISTANCE) {
            throw new IllegalArgumentException(
                    "Render distance must be between " + MIN_RENDER_DISTANCE + " and " + MAX_RENDER_DISTANCE);
        }
        if (renderDistance == value) return false;
        renderDistance = value;
        return true;
    }
}
