package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class ItemIconsTest {
    @Test
    void emptySingleAndFullStackHaveConsistentIconAndCompactBottomRightCounts() {
        var canvas = new UiCanvas();
        ItemIcons.render(canvas, 10, 20, 20, BlockId.AIR, 0);
        assertEquals(0, canvas.vertexCount());
        ItemIcons.render(canvas, 10, 20, 20, BlockId.POPPY, 1);
        assertEquals(6, canvas.vertexCount());
        float[] single = vertices(canvas);
        assertEquals(12, single[0]);
        assertEquals(22, single[1]);
        assertEquals(16, single[8] - single[0]);
        canvas.clear();
        ItemIcons.render(canvas, 10, 20, 20, BlockId.POPPY, 64);
        assertEquals(30, canvas.vertexCount());
        float[] stacked = vertices(canvas);
        assertArrayEquals(single, java.util.Arrays.copyOf(stacked, 48));
        assertEquals(UiAtlas.digit(6).x() / (float) UiAtlas.WIDTH, stacked[48 + 6]);
        assertEquals(UiAtlas.digit(4).x() / (float) UiAtlas.WIDTH, stacked[96 + 6]);
        for (int offset = 48; offset < stacked.length; offset += 8) {
            assertTrue(stacked[offset] >= 10 && stacked[offset] <= 30);
            assertTrue(stacked[offset + 1] >= 20 && stacked[offset + 1] <= 40);
        }
        canvas.clear();
        ItemIcons.render(canvas, 10, 20, 4, BlockId.POPPY, 64);
        assertEquals(6, canvas.vertexCount());
        assertThrows(IllegalArgumentException.class, () -> canvas.numberWithShadow(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> canvas.sprite(UiAtlas.HOTBAR, 0, 0, 0, 22));
    }

    private static float[] vertices(UiCanvas canvas) {
        var data = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(data);
        return data.array();
    }
}
