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
        float u = .5f / UiAtlas.WIDTH, v = .5f / UiAtlas.HEIGHT;
        quad(x, y, width, height, r, g, b, a, u, v, u, v);
    }

    public void sprite(UiAtlas.Region region, int x, int y, int width, int height) {
        sprite(region, x, y, width, height, 1, 1, 1);
    }

    private void sprite(UiAtlas.Region region, int x, int y, int width, int height, float r, float g, float b) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Sprite dimensions must be positive");
        quad(
                x,
                y,
                width,
                height,
                r,
                g,
                b,
                1,
                region.x() / (float) UiAtlas.WIDTH,
                region.y() / (float) UiAtlas.HEIGHT,
                (region.x() + region.width()) / (float) UiAtlas.WIDTH,
                (region.y() + region.height()) / (float) UiAtlas.HEIGHT);
    }

    public void numberWithShadow(int number, int x, int y) {
        if (number < 0) throw new IllegalArgumentException("Number must be nonnegative");
        String digits = Integer.toString(number);
        for (int pass = 0; pass < 2; pass++) {
            int offset = pass == 0 ? 1 : 0;
            float shade = pass == 0 ? .25f : 1;
            for (int index = 0; index < digits.length(); index++)
                sprite(
                        UiAtlas.digit(digits.charAt(index) - '0'),
                        x + index * 4 + offset,
                        y + offset,
                        3,
                        7,
                        shade,
                        shade,
                        shade);
        }
    }

    public void textWithShadow(BitmapFont font, String text, int x, int y, float r, float g, float b, float a) {
        text(font, text, x + 1, y + 1, r * .25f, g * .25f, b * .25f, a);
        text(font, text, x, y, r, g, b, a);
    }

    public void textWithShadow(
            BitmapFont font, String text, int x, int y, double scale, float r, float g, float b, float a) {
        int shadowOffset = Math.max(1, (int) Math.round(scale));
        text(font, text, x + shadowOffset, y + shadowOffset, scale, r * .25f, g * .25f, b * .25f, a);
        text(font, text, x, y, scale, r, g, b, a);
    }

    public void text(BitmapFont font, String text, int x, int y, double scale, float r, float g, float b, float a) {
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Text scale must be positive");
        int cursorX = x, cursorY = y;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                cursorX = x;
                cursorY += (int) Math.round(BitmapFont.LINE_HEIGHT * scale);
                continue;
            }
            int glyph = font.glyph(codePoint), width = font.glyphWidth(codePoint);
            if (width > 0) {
                float u = glyph % 16 * BitmapFont.CELL_SIZE / (float) UiAtlas.WIDTH;
                float v = glyph / 16 * BitmapFont.CELL_SIZE / (float) UiAtlas.HEIGHT;
                quad(
                        cursorX,
                        cursorY,
                        Math.max(1, (int) Math.round(width * scale)),
                        Math.max(1, (int) Math.round(BitmapFont.GLYPH_HEIGHT * scale)),
                        r,
                        g,
                        b,
                        a,
                        u,
                        v,
                        u + width / (float) UiAtlas.WIDTH,
                        v + BitmapFont.GLYPH_HEIGHT / (float) UiAtlas.HEIGHT);
            }
            cursorX += Math.max(1, (int) Math.round(font.advance(codePoint) * scale));
        }
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
                float u = glyph % 16 * BitmapFont.CELL_SIZE / (float) UiAtlas.WIDTH;
                float v = glyph / 16 * BitmapFont.CELL_SIZE / (float) UiAtlas.HEIGHT;
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
                        u + width / (float) UiAtlas.WIDTH,
                        v + BitmapFont.GLYPH_HEIGHT / (float) UiAtlas.HEIGHT);
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
