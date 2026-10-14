package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

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
        var inventory = new Inventory();
        var hotbar = new Hotbar();
        for (int id = 1; id < 256; id++) {
            if (!BlockId.isValid((byte) id)) continue;
            inventory.creativePick(0, (byte) id);
            hotbar.inventory(inventory.snapshot());
            var canvas = new UiCanvas();
            new Hud().render(canvas, hotbar, new UiViewport(1280, 720, 1280, 720));
            float[] data = vertices(canvas);
            var region = UiAtlas.item((byte) id);
            assertEquals(region.x() / (float) UiAtlas.WIDTH, data[2 * 48 + 6]);
            assertEquals(region.y() / (float) UiAtlas.HEIGHT, data[2 * 48 + 7]);
            assertEquals(HudMetrics.ITEM_SIZE, data[2 * 48 + 8] - data[2 * 48]);
        }
    }

    @ParameterizedTest
    @CsvSource({"640,360", "1280,720", "1920,1080", "1281,721", "180,100"})
    void scaledHudKeepsEverySlotInsideFramebuffer(int width, int height) {
        var viewport = new UiViewport(width, height, width, height);
        var canvas = new UiCanvas();
        var hotbar = new Hotbar();
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            canvas.clear();
            hotbar.select(slot);
            new Hud().render(canvas, hotbar, viewport);
            float[] data = vertices(canvas);
            for (int offset = 0; offset < data.length; offset += UiCanvas.FLOATS_PER_VERTEX) {
                assertTrue(data[offset] * viewport.scale() >= 0 && data[offset] * viewport.scale() <= width);
                assertTrue(data[offset + 1] * viewport.scale() >= 0 && data[offset + 1] * viewport.scale() <= height);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4", "0"})
    void hudUsesLogicalMetricsAtEveryRequestedScale(int requested) {
        var viewport = new UiViewport(1920, 1080, 1920, 1080, requested);
        var canvas = new UiCanvas();
        var hotbar = new Hotbar();
        new Hud().render(canvas, hotbar, viewport);
        float[] data = vertices(canvas);
        assertEquals(HudMetrics.CROSSHAIR_SIZE, data[8] - data[0]);
        assertEquals(HudMetrics.CROSSHAIR_SIZE, data[17] - data[1]);
        int bar = 48;
        assertEquals(HudMetrics.HOTBAR_WIDTH, data[bar + 8] - data[bar]);
        assertEquals(HudMetrics.HOTBAR_HEIGHT, data[bar + 17] - data[bar + 1]);
        assertEquals((int) viewport.height(), data[bar + 17]);
        int frame = data.length - 48;
        assertEquals(HudMetrics.SLOT_FRAME_WIDTH, data[frame + 8] - data[frame]);
        assertEquals(HudMetrics.SLOT_FRAME_HEIGHT, data[frame + 17] - data[frame + 1]);
        assertEquals(21 * 6, canvas.vertexCount());
        float firstFrameX = data[frame];
        hotbar.select(8);
        canvas.clear();
        new Hud().render(canvas, hotbar, viewport);
        assertEquals(firstFrameX + 8 * HudMetrics.SLOT_SPACING, vertices(canvas)[frame]);
    }

    private static float[] vertices(UiCanvas canvas) {
        FloatBuffer target = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(target);
        return target.array();
    }
}
