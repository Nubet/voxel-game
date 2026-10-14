package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.core.Hotbar;
import com.norbertfila.player.Inventory;
import com.norbertfila.world.BlockId;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HudTest {
    @Test
    void populatedHotbarRendersAllCurrentBlockIdsIncludingPlantsAndCounts() {
        Hotbar hotbar = new Hotbar();
        var inventory = new Inventory();
        for (int id = 1; id < 256; id++) {
            if (!BlockId.isValid((byte) id)) continue;
            inventory.creativePick(0, (byte) id);
            hotbar.inventory(inventory.snapshot());
            UiCanvas populated = new UiCanvas(), empty = new UiCanvas();
            UiViewport viewport = new UiViewport(1280, 720, 1280, 720);
            new Hud().render(populated, hotbar, viewport);
            new Hud().render(empty, new Hotbar(), viewport);
            assertTrue(populated.floatCount() > empty.floatCount());
        }
    }

    @ParameterizedTest
    @CsvSource({"640,360", "1280,720", "1920,1080", "1281,721", "180,100"})
    void scaledHudKeepsEverySlotInsideFramebuffer(int width, int height) {
        UiViewport viewport = new UiViewport(width, height, width, height);
        UiCanvas canvas = new UiCanvas();
        new Hud().render(canvas, new Hotbar(), viewport);
        float[] vertices = vertices(canvas);
        int count = canvas.floatCount();
        int slots = 0;
        for (int offset = 0; offset < count; offset += 8) {
            assertTrue(vertices[offset] * viewport.scale() >= 0 && vertices[offset] * viewport.scale() <= width);
            assertTrue(
                    vertices[offset + 1] * viewport.scale() >= 0 && vertices[offset + 1] * viewport.scale() <= height);
        }
        for (int offset = 0; offset < count; offset += 48) {
            float r = vertices[offset + 2], g = vertices[offset + 3], b = vertices[offset + 4];
            if (r == .25f && g == .29f && b == .35f) slots++;
        }
        assertEquals(Hotbar.SLOTS, slots);
    }

    @ParameterizedTest
    @CsvSource({"1280,720", "640,360", "180,100"})
    void hudHasCenteredCrosshairAllSlotsAndOneMovableSelection(int width, int height) {
        UiCanvas canvas = new UiCanvas();
        Hud hud = new Hud();
        UiViewport viewport = new UiViewport(width, height, width, height);
        Hotbar hotbar = new Hotbar();
        hotbar.select(Hotbar.SLOTS - 1);
        hud.render(canvas, hotbar, viewport);
        float[] vertices = vertices(canvas);
        int count = canvas.floatCount();
        assertTrue(count > 0 && count <= vertices.length);
        assertEquals(0, count % 48);
        assertEquals((int) viewport.width() / 2 - 7, vertices[0]);
        assertEquals((int) viewport.height() / 2 - 1, vertices[1]);
        int selected = 0;
        int slots = 0;
        for (int offset = 0; offset < count; offset += 48) {
            float r = vertices[offset + 2];
            float g = vertices[offset + 3];
            float b = vertices[offset + 4];
            boolean selectedBorder = r == .96f && g == .8f && b == .3f;
            boolean ordinaryBorder = r == .25f && g == .29f && b == .35f;
            if (selectedBorder) selected++;
            if (ordinaryBorder) slots++;
        }
        assertEquals(4, selected);
        assertEquals(Hotbar.SLOTS, slots);
        for (int offset = 0; offset < count; offset += 8) {
            assertTrue(vertices[offset] >= 0 && vertices[offset] <= viewport.width());
            assertTrue(vertices[offset + 1] >= 0 && vertices[offset + 1] <= viewport.height());
        }
        hotbar.select(0);
        canvas.clear();
        hud.render(canvas, hotbar, viewport);
        assertEquals(count, canvas.floatCount());
        vertices = vertices(canvas);
        assertEquals(.12f, vertices[4 * 48 + 2]);
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4", "0"})
    void hudUsesLogicalMetricsAtEveryRequestedScale(int requested) {
        var viewport = new UiViewport(1920, 1080, 1920, 1080, requested);
        var canvas = new UiCanvas();
        new Hud().render(canvas, new Hotbar(), viewport);
        float[] data = vertices(canvas);

        assertEquals(HudMetrics.CROSSHAIR_SIZE, data[8] - data[0]);
        int bar = 4 * 48;
        assertEquals(HudMetrics.HOTBAR_WIDTH, data[bar + 8] - data[bar]);
        assertEquals(HudMetrics.HOTBAR_HEIGHT, data[bar + 17] - data[bar + 1]);
        assertEquals((int) viewport.height(), data[bar + 17]);
        int frame = data.length - 4 * 48;
        assertEquals(HudMetrics.SLOT_FRAME_WIDTH, data[frame + 8] - data[frame]);
        assertEquals(HudMetrics.SLOT_FRAME_HEIGHT, data[frame + 48 + 17] - data[frame + 48 + 1]);
    }

    private static float[] vertices(UiCanvas canvas) {
        FloatBuffer target = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(target);
        return target.array();
    }
}
