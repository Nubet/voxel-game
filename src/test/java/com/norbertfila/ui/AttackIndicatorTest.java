package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class AttackIndicatorTest {
    private static final UiViewport VIEWPORT = new UiViewport(1280, 720, 1280, 720);

    @Test
    void showsACenteredBarUnderTheCrosshairOnlyWhileRecharging() {
        var canvas = new UiCanvas();
        new AttackIndicator().render(canvas, VIEWPORT, 1, 1);
        assertEquals(0, canvas.vertexCount());
        new AttackIndicator().render(canvas, VIEWPORT, .5, 2);
        assertEquals(12, canvas.vertexCount());
        var vertices = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(vertices);
        assertEquals(VIEWPORT.width() / 2 - AttackIndicator.WIDTH, vertices.get(0), 1e-3);
        assertTrue(vertices.get(1) > VIEWPORT.height() / 2 + HudMetrics.CROSSHAIR_SIZE);
        assertThrows(IllegalArgumentException.class, () -> new AttackIndicator().render(canvas, VIEWPORT, 2, 1));
    }
}
