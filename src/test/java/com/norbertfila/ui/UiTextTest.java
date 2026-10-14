package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class UiTextTest {
    private static final BitmapFont FONT = BitmapFont.DEFAULT;

    @Test
    void textUsesMeasuredAdvancesAndCorrectAtlasRegion() {
        UiCanvas canvas = new UiCanvas();
        canvas.text(FONT, "A i\n?", 10, 20, 1, 1, 1, 1);
        assertEquals(18, canvas.vertexCount());
        float[] data = vertices(canvas);
        assertEquals(10, data[0]);
        assertEquals(20, data[1]);
        assertEquals(10 + FONT.width("A "), data[48]);
        assertEquals(10, data[96]);
        assertEquals(20 + BitmapFont.LINE_HEIGHT, data[97]);
        assertEquals('A' % 16 * 16 / (float) BitmapFont.ATLAS_WIDTH, data[6]);
        assertEquals('A' / 16 * 16 / (float) BitmapFont.ATLAS_HEIGHT, data[7]);
        assertEquals(10 + FONT.glyphWidth('A'), data[8]);
        assertEquals(20 + BitmapFont.GLYPH_HEIGHT, data[17]);
    }

    @Test
    void shadowPrecedesForegroundAndRetainsAlpha() {
        UiCanvas canvas = new UiCanvas();
        canvas.textWithShadow(FONT, "A", 10, 20, 1, .8f, .4f, .5f);
        assertEquals(12, canvas.vertexCount());
        float[] data = vertices(canvas);
        assertEquals(11, data[0]);
        assertEquals(21, data[1]);
        assertEquals(.25f, data[2]);
        assertEquals(.5f, data[5]);
        assertEquals(10, data[48]);
        assertEquals(20, data[49]);
        assertEquals(1, data[50]);
        assertEquals(.5f, data[53]);
    }

    @Test
    void unsupportedCodePointEmitsOneFallbackAndWhitespaceEmitsNoQuads() {
        UiCanvas fallback = new UiCanvas(), unsupported = new UiCanvas();
        fallback.text(FONT, "?", 0, 0, 1, 1, 1, 1);
        unsupported.text(FONT, "\uD83C\uDF32", 0, 0, 1, 1, 1, 1);
        assertArrayEquals(vertices(fallback), vertices(unsupported));
        unsupported.clear();
        unsupported.textWithShadow(FONT, " \n ", 0, 0, 1, 1, 1, 1);
        assertEquals(0, unsupported.vertexCount());
    }

    private static float[] vertices(UiCanvas canvas) {
        FloatBuffer data = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(data);
        return data.array();
    }
}
