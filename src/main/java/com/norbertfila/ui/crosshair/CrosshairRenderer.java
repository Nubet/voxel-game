package com.norbertfila.ui.crosshair;

import com.norbertfila.ui.HudMetrics;
import com.norbertfila.ui.UiAtlas;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;
import java.util.Objects;

public final class CrosshairRenderer {
    public void render(UiCanvas canvas, UiViewport viewport) {
        render(canvas, viewport, CrosshairStyle.DEFAULT, CrosshairSettings.DEFAULT, 1);
    }

    public void render(
            UiCanvas canvas, UiViewport viewport, CrosshairStyle style, CrosshairSettings settings, float visibility) {
        renderAt(canvas, (float) viewport.width() / 2, (float) viewport.height() / 2, style, settings, visibility);
    }

    public void renderAt(
            UiCanvas canvas,
            float centerX,
            float centerY,
            CrosshairStyle style,
            CrosshairSettings settings,
            float visibility) {
        Objects.requireNonNull(style);
        Objects.requireNonNull(settings);
        if (!Float.isFinite(visibility) || visibility < 0 || visibility > 1)
            throw new IllegalArgumentException("Crosshair visibility must be between 0 and 1");
        float alpha = settings.opacity() / 100f * visibility;
        if (style == CrosshairStyle.HIDDEN || alpha == 0) return;
        int scale = settings.scale();
        float size = HudMetrics.CROSSHAIR_SIZE * scale;
        float left = centerX - size / 2, top = centerY - size / 2;
        if (style == CrosshairStyle.DEFAULT) {
            canvas.sprite(UiAtlas.CROSSHAIR, left, top, size, size, alpha);
            return;
        }
        for (int y = -7; y <= 7; y++) {
            for (int x = -7; x <= 7; x++) {
                boolean foreground = filled(style, x, y);
                if (!foreground && !border(style, x, y)) continue;
                float shade = foreground ? 1 : .08f;
                canvas.rectangle(
                        left + (x + 7) * scale, top + (y + 7) * scale, scale, scale, shade, shade, shade, alpha);
            }
        }
    }

    private static boolean filled(CrosshairStyle style, int x, int y) {
        return switch (style) {
            case BLOCK -> Math.max(Math.abs(x), Math.abs(y)) == 3;
            case ENTITY -> (x == 0 && Math.abs(y) <= 5) || (y == 0 && Math.abs(x) <= 5);
            case RANGED ->
                (x == 0 && y == 0)
                        || (x == 0 && Math.abs(y) >= 3 && Math.abs(y) <= 5)
                        || (y == 0 && Math.abs(x) >= 3 && Math.abs(x) <= 5);
            default -> false;
        };
    }

    private static boolean border(CrosshairStyle style, int x, int y) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) if (filled(style, x + dx, y + dy)) return true;
        return false;
    }
}
