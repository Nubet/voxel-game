package com.norbertfila.ui.crosshair;

public record CrosshairSettings(boolean dynamic, boolean hideWithoutTarget, boolean smooth, int scale, int opacity) {
    public static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 4;
    public static final CrosshairSettings DEFAULT = new CrosshairSettings(true, true, true, 1, 100);

    public CrosshairSettings {
        if (scale < MIN_SCALE || scale > MAX_SCALE)
            throw new IllegalArgumentException("Crosshair scale must be between 1 and 4");
        if (opacity < 0 || opacity > 100)
            throw new IllegalArgumentException("Crosshair opacity must be between 0 and 100");
    }
}
