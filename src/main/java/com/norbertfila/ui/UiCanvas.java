package com.norbertfila.ui;

import java.nio.FloatBuffer;
import java.util.Arrays;

public final class UiCanvas {
    public static final int FLOATS_PER_VERTEX = 8;
    private static final int[] RECTANGLE_CORNERS = {0, 1, 2, 2, 3, 0};
    private float[] vertices = new float[6 * FLOATS_PER_VERTEX * 32];
    private int floatCount;

    public void clear() {
        floatCount = 0;
    }

    public int floatCount() {
        return floatCount;
    }

    public int vertexCount() {
        return floatCount / FLOATS_PER_VERTEX;
    }

    public void writeTo(FloatBuffer target) {
        target.put(vertices, 0, floatCount);
    }

    public void rectangle(int x, int y, int width, int height, float r, float g, float b, float a) {
        float u = .5f / BitmapFont.ATLAS_WIDTH, v = .5f / BitmapFont.ATLAS_HEIGHT;
        quad(x, y, width, height, r, g, b, a, u, v, u, v);
    }

    public void textWithShadow(BitmapFont font, String text, int x, int y, float r, float g, float b, float a) {
        text(font, text, x + 1, y + 1, r * .25f, g * .25f, b * .25f, a);
        text(font, text, x, y, r, g, b, a);
    }

    public void text(BitmapFont font, String text, int x, int y, float r, float g, float b, float a) {
        int cursorX = x, cursorY = y;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                cursorX = x;
                cursorY += BitmapFont.LINE_HEIGHT;
                continue;
            }
            int glyph = font.glyph(codePoint), width = font.glyphWidth(codePoint);
            if (width > 0) {
                float u = glyph % 16 * BitmapFont.CELL_SIZE / (float) BitmapFont.ATLAS_WIDTH;
                float v = glyph / 16 * BitmapFont.CELL_SIZE / (float) BitmapFont.ATLAS_HEIGHT;
                quad(
                        cursorX,
                        cursorY,
                        width,
                        BitmapFont.GLYPH_HEIGHT,
                        r,
                        g,
                        b,
                        a,
                        u,
                        v,
                        u + width / (float) BitmapFont.ATLAS_WIDTH,
                        v + BitmapFont.GLYPH_HEIGHT / (float) BitmapFont.ATLAS_HEIGHT);
            }
            cursorX += font.advance(codePoint);
        }
    }

    private void quad(
            int x,
            int y,
            int width,
            int height,
            float r,
            float g,
            float b,
            float a,
            float u0,
            float v0,
            float u1,
            float v1) {
        int required = Math.addExact(floatCount, 6 * FLOATS_PER_VERTEX);
        if (required > vertices.length)
            vertices = Arrays.copyOf(vertices, Math.max(required, Math.multiplyExact(vertices.length, 2)));
        for (int index : RECTANGLE_CORNERS) {
            vertices[floatCount++] = index == 1 || index == 2 ? x + width : x;
            vertices[floatCount++] = index >= 2 ? y + height : y;
            vertices[floatCount++] = r;
            vertices[floatCount++] = g;
            vertices[floatCount++] = b;
            vertices[floatCount++] = a;
            vertices[floatCount++] = index == 1 || index == 2 ? u1 : u0;
            vertices[floatCount++] = index >= 2 ? v1 : v0;
        }
    }
}
