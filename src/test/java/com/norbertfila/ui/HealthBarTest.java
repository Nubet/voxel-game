package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealthBarTest {
    private static final UiViewport VIEWPORT = new UiViewport(1280, 720, 1280, 720);

    @Test
    void drawsTenContainersAndFillsByTwoPointsPerHeart() {
        assertEquals(10 + 10, sprites(20));
        assertEquals(10 + 5, sprites(10));
        assertEquals(10 + 6, sprites(11));
        assertEquals(10, sprites(0));
    }

    @Test
    void sitsAboveTheLeftHalfOfTheHotbar() {
        var canvas = new UiCanvas();
        new HealthBar().render(canvas, VIEWPORT, 20, 20, false);
        var vertices = java.nio.FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(vertices);
        float x = vertices.get(0), y = vertices.get(1);
        assertEquals((VIEWPORT.width() - HudMetrics.HOTBAR_WIDTH) / 2, x, 1);
        assertTrue(y < VIEWPORT.height() - HudMetrics.HOTBAR_HEIGHT);
    }

    private static int sprites(float health) {
        var canvas = new UiCanvas();
        new HealthBar().render(canvas, VIEWPORT, health, 20, false);
        return canvas.vertexCount() / 6;
    }
}
