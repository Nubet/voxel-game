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
        assertEquals((float) viewport.width() / 2 - 7.5f, vertices.get(0));
        assertEquals((float) viewport.height() / 2 - 7.5f, vertices.get(1));
    }

    @Test
    void allStylesShareTheExactCenterAndApplyOpacityWithoutOverlappingPixels() {
        var renderer = new CrosshairRenderer();
        for (int width : new int[] {1280, 1281}) {
            for (int requested = 0; requested <= 4; requested++) {
                var viewport = new UiViewport(width, 721, width, 721, requested);
                for (var style : CrosshairStyle.values()) {
                    for (int scale = 1; scale <= 4; scale++) {
                        var canvas = new UiCanvas();
                        renderer.render(
                                canvas, viewport, style, new CrosshairSettings(true, true, true, scale, 50), .5f);
                        if (style == CrosshairStyle.HIDDEN) {
                            assertEquals(0, canvas.vertexCount());
                            continue;
                        }
                        var data = FloatBuffer.allocate(canvas.floatCount());
                        canvas.writeTo(data);
                        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
                        float minY = minX, maxY = maxX;
                        for (int i = 0; i < data.position(); i += 8) {
                            minX = Math.min(minX, data.get(i));
                            maxX = Math.max(maxX, data.get(i));
                            minY = Math.min(minY, data.get(i + 1));
                            maxY = Math.max(maxY, data.get(i + 1));
                            assertEquals(.25f, data.get(i + 5));
                        }
                        assertEquals((float) viewport.width() / 2, (minX + maxX) / 2, .0001);
                        assertEquals((float) viewport.height() / 2, (minY + maxY) / 2, .0001);
                    }
                }
            }
        }
        var canvas = new UiCanvas();
        renderer.renderAt(canvas, 50, 50, CrosshairStyle.BLOCK, new CrosshairSettings(true, true, true, 1, 0), 1);
        assertEquals(0, canvas.vertexCount());
    }
}
