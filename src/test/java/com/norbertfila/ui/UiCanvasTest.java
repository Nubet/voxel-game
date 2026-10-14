package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.BufferOverflowException;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class UiCanvasTest {
    @Test
    void growsWithoutLosingOrderAndCanBeClearedAndReused() {
        UiCanvas canvas = new UiCanvas();
        for (int i = 0; i < 3000; i++) canvas.rectangle(i, i + 1, 2, 3, .1f, .2f, .3f, .4f);
        assertEquals(3000 * 6, canvas.vertexCount());
        FloatBuffer target = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(target);
        assertEquals(target.capacity(), target.position());
        target.flip();
        for (int i = 0; i < 3000; i++) {
            float[] quad = new float[48];
            target.get(quad);
            assertEquals(i, quad[0]);
            assertEquals(i + 1, quad[1]);
            assertEquals(i + 2, quad[8]);
            assertEquals(i + 4, quad[17]);
            for (int vertex = 0; vertex < 6; vertex++)
                assertArrayEquals(
                        new float[] {.1f, .2f, .3f, .4f},
                        java.util.Arrays.copyOfRange(quad, vertex * 8 + 2, vertex * 8 + 6));
            assertEquals(.5f / BitmapFont.ATLAS_WIDTH, quad[6]);
            assertEquals(.5f / BitmapFont.ATLAS_HEIGHT, quad[7]);
        }
        canvas.clear();
        assertEquals(0, canvas.vertexCount());
        assertEquals(0, canvas.floatCount());
        canvas.rectangle(7, 8, 9, 10, 1, 0, 0, 1);
        FloatBuffer reused = FloatBuffer.allocate(48);
        canvas.writeTo(reused);
        assertEquals(48, reused.position());
        assertEquals(7, reused.get(0));
        assertEquals(18, reused.get(17));
    }

    @Test
    void writingDoesNotExposeOrConsumeCanvasAndHonorsTargetPosition() {
        UiCanvas canvas = new UiCanvas();
        canvas.rectangle(1, 2, 3, 4, 1, 1, 1, 1);
        FloatBuffer target = FloatBuffer.allocate(52);
        target.position(4);
        canvas.writeTo(target);
        target.put(4, 99);
        FloatBuffer second = FloatBuffer.allocate(48);
        canvas.writeTo(second);
        assertEquals(1, second.get(0));
        assertEquals(6, canvas.vertexCount());
        assertThrows(BufferOverflowException.class, () -> canvas.writeTo(FloatBuffer.allocate(47)));
        canvas.clear();
        canvas.writeTo(second);
        assertEquals(48, second.position());
    }

    @Test
    void scaledTextKeepsOneQuadPerGlyphAndUsesCompactDimensions() {
        UiCanvas canvas = new UiCanvas();
        canvas.textWithShadow(BitmapFont.DEFAULT, "9", 2, 3, .5, 1, 1, 1, 1);
        FloatBuffer data = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(data);
        assertEquals(12, canvas.vertexCount());
        assertEquals(2, data.get(48), 0.001);
        assertEquals(3, data.get(49), 0.001);
        assertEquals(6, data.get(56), 0.001);
        assertEquals(3, data.get(57), 0.001);
        assertEquals(6, data.get(64), 0.001);
        assertEquals(10, data.get(65), 0.001);
    }
}
