package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.core.Hotbar;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UiRendererTest {
    @ParameterizedTest
    @CsvSource({"1280,720", "640,360", "180,100"})
    void hudHasCenteredCrosshairNineSlotsAndOneMovableSelection(int width, int height) {
        float[] vertices = new float[36 * 96];
        Hotbar hotbar = new Hotbar();
        hotbar.select(8);
        int count = UiRenderer.hudVertices(vertices, hotbar, width, height);
        assertTrue(count > 0 && count <= vertices.length);
        assertEquals(0, count % 36);
        assertEquals(width / 2 - 8, vertices[0]);
        assertEquals(height / 2 - 2, vertices[1]);
        int selected = 0;
        int slots = 0;
        for (int offset = 0; offset < count; offset += 36) {
            float r = vertices[offset + 2];
            if (r == .96f) selected++;
            if (r == .96f || r == .25f) slots++;
        }
        assertEquals(1, selected);
        assertEquals(9, slots);
        for (int offset = 0; offset < count; offset += 6) {
            assertTrue(vertices[offset] >= 0 && vertices[offset] <= width);
            assertTrue(vertices[offset + 1] >= 0 && vertices[offset + 1] <= height);
        }
        hotbar.select(0);
        assertEquals(count, UiRenderer.hudVertices(vertices, hotbar, width, height));
        assertEquals(.96f, vertices[4 * 36 + 2]);
    }
}
