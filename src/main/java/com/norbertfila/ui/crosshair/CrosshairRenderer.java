package com.norbertfila.ui.crosshair;

import com.norbertfila.ui.HudMetrics;
import com.norbertfila.ui.UiAtlas;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;

public final class CrosshairRenderer {
    public void render(UiCanvas canvas, UiViewport viewport) {
        render(canvas, viewport, CrosshairStyle.DEFAULT);
    }

    public void render(UiCanvas canvas, UiViewport viewport, CrosshairStyle style) {
        if (style == CrosshairStyle.HIDDEN) return;
        if (style == CrosshairStyle.BLOCK) {
            int x = (int) viewport.width() / 2 - 3, y = (int) viewport.height() / 2 - 3;
            canvas.rectangle(x, y, 7, 1, 1, 1, 1, 1);
            canvas.rectangle(x, y + 6, 7, 1, 1, 1, 1, 1);
            canvas.rectangle(x, y + 1, 1, 5, 1, 1, 1, 1);
            canvas.rectangle(x + 6, y + 1, 1, 5, 1, 1, 1, 1);
            return;
        }
        int size = HudMetrics.CROSSHAIR_SIZE;
        canvas.sprite(
                UiAtlas.CROSSHAIR,
                (int) viewport.width() / 2 - size / 2,
                (int) viewport.height() / 2 - size / 2,
                size,
                size);
    }
}
