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
            float[] quad = new float[36];
            target.get(quad);
            assertEquals(i, quad[0]);
            assertEquals(i + 1, quad[1]);
            assertEquals(i + 2, quad[6]);
            assertEquals(i + 4, quad[13]);
            for (int vertex = 0; vertex < 6; vertex++)
                assertArrayEquals(
                        new float[] {.1f, .2f, .3f, .4f},
                        java.util.Arrays.copyOfRange(quad, vertex * 6 + 2, vertex * 6 + 6));
        }
        canvas.clear();
        assertEquals(0, canvas.vertexCount());
        assertEquals(0, canvas.floatCount());
        canvas.rectangle(7, 8, 9, 10, 1, 0, 0, 1);
        FloatBuffer reused = FloatBuffer.allocate(36);
        canvas.writeTo(reused);
        assertEquals(36, reused.position());
        assertEquals(7, reused.get(0));
        assertEquals(18, reused.get(13));
    }

    @Test
    void writingDoesNotExposeOrConsumeCanvasAndHonorsTargetPosition() {
        UiCanvas canvas = new UiCanvas();
        canvas.rectangle(1, 2, 3, 4, 1, 1, 1, 1);
        FloatBuffer target = FloatBuffer.allocate(40);
        target.position(4);
        canvas.writeTo(target);
        target.put(4, 99);
        FloatBuffer second = FloatBuffer.allocate(36);
        canvas.writeTo(second);
        assertEquals(1, second.get(0));
        assertEquals(6, canvas.vertexCount());
        assertThrows(BufferOverflowException.class, () -> canvas.writeTo(FloatBuffer.allocate(35)));
        canvas.clear();
        canvas.writeTo(second);
        assertEquals(36, second.position());
    }
}
