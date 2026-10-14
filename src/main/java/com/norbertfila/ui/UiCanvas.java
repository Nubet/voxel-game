package com.norbertfila.ui;

import java.nio.FloatBuffer;
import java.util.Arrays;

public final class UiCanvas {
    public static final int FLOATS_PER_VERTEX = 6;
    private static final int[] RECTANGLE_CORNERS = {0, 1, 2, 2, 3, 0};
    private float[] vertices = new float[36 * 32];
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
        int required = Math.addExact(floatCount, 36);
        if (required > vertices.length)
            vertices = Arrays.copyOf(vertices, Math.max(required, Math.multiplyExact(vertices.length, 2)));
        for (int index : RECTANGLE_CORNERS) {
            vertices[floatCount++] = index == 1 || index == 2 ? x + width : x;
            vertices[floatCount++] = index >= 2 ? y + height : y;
            vertices[floatCount++] = r;
            vertices[floatCount++] = g;
            vertices[floatCount++] = b;
            vertices[floatCount++] = a;
        }
    }
}
