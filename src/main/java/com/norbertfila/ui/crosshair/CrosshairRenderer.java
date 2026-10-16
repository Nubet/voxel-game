package com.norbertfila.ui.crosshair;

import com.norbertfila.ui.HudMetrics;
import com.norbertfila.ui.UiAtlas;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;

public final class CrosshairRenderer {
    public void render(UiCanvas canvas, UiViewport viewport) {
        int size = HudMetrics.CROSSHAIR_SIZE;
        canvas.sprite(
                UiAtlas.CROSSHAIR,
                (int) viewport.width() / 2 - size / 2,
                (int) viewport.height() / 2 - size / 2,
                size,
                size);
    }
}
