package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.core.Hotbar;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UiRendererTest {
    @ParameterizedTest
    @CsvSource({"1280,720", "640,360", "180,100"})
    void hudHasCenteredCrosshairAllSlotsAndOneMovableSelection(int width, int height) {
        float[] vertices = new float[36 * (Hotbar.SLOTS * 11 + 4)];
        Hotbar hotbar = new Hotbar();
        hotbar.select(Hotbar.SLOTS - 1);
        int count = UiRenderer.hudVertices(vertices, hotbar, width, height);
        assertTrue(count > 0 && count <= vertices.length);
        assertEquals(0, count % 36);
        assertEquals(width / 2 - 8, vertices[0]);
        assertEquals(height / 2 - 2, vertices[1]);
        int selected = 0;
        int slots = 0;
        for (int offset = 0; offset < count; offset += 36) {
            float r = vertices[offset + 2];
            float g = vertices[offset + 3];
            float b = vertices[offset + 4];
            boolean selectedBorder = r == .96f && g == .8f && b == .3f;
            boolean ordinaryBorder = r == .25f && g == .29f && b == .35f;
            if (selectedBorder) selected++;
            if (selectedBorder || ordinaryBorder) slots++;
        }
        assertEquals(1, selected);
        assertEquals(Hotbar.SLOTS, slots);
        for (int offset = 0; offset < count; offset += 6) {
            assertTrue(vertices[offset] >= 0 && vertices[offset] <= width);
            assertTrue(vertices[offset + 1] >= 0 && vertices[offset + 1] <= height);
        }
        hotbar.select(0);
        assertEquals(count, UiRenderer.hudVertices(vertices, hotbar, width, height));
        assertEquals(.96f, vertices[4 * 36 + 2]);
    }
}
