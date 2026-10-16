package com.norbertfila.ui.crosshair;

import com.norbertfila.ui.HudMetrics;
import com.norbertfila.ui.UiAtlas;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;
import java.util.Objects;

public final class CrosshairRenderer {
    private static final CrosshairStyle[] STYLES = CrosshairStyle.values();

    public void render(
            UiCanvas canvas, UiViewport viewport, CrosshairTransition transition, CrosshairSettings settings) {
        for (var style : STYLES) {
            float visibility = transition.visibility(style);
            if (visibility > 0) render(canvas, viewport, style, settings, visibility);
        }
    }

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
        var region = switch (style) {
            case DEFAULT -> UiAtlas.CROSSHAIR;
            case BLOCK -> UiAtlas.CROSSHAIR_BLOCK;
            case ENTITY -> UiAtlas.CROSSHAIR_ENTITY;
            case RANGED -> UiAtlas.CROSSHAIR_RANGED;
            case HIDDEN -> throw new IllegalStateException("Hidden crosshair has no sprite");
        };
        canvas.sprite(region, left, top, size, size, alpha);
        if (style == CrosshairStyle.RANGED) canvas.sprite(UiAtlas.CROSSHAIR_DOT, left, top, size, size, alpha);
    }
}
