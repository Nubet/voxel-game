package com.norbertfila.ui.crosshair;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.HudMetrics;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class CrosshairRendererTest {
    @Test
    void rendersTheStaticSpriteIndependentlyOfHotbar() {
        var viewport = new UiViewport(1280, 720, 1280, 720);
        var canvas = new UiCanvas();
        new CrosshairRenderer().render(canvas, viewport);
        var vertices = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(vertices);
        assertEquals(6, canvas.vertexCount());
        assertEquals(HudMetrics.CROSSHAIR_SIZE, vertices.get(8) - vertices.get(0));
        assertEquals((int) viewport.width() / 2 - 7, vertices.get(0));
        assertEquals((int) viewport.height() / 2 - 7, vertices.get(1));
    }
}
