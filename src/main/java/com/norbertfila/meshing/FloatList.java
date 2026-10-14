package com.norbertfila.meshing;

import java.util.Arrays;

/** Growable float buffer reused across meshing calls on one worker thread. */
final class FloatList {
    // Buffers that grew past this size after a pathological mesh are dropped so a worker does not keep them.
    private static final int RETAINED_CAPACITY = 1 << 18;
    private static final int INITIAL_CAPACITY = 4096;
    private float[] values = new float[INITIAL_CAPACITY];
    private int size;

    void add(float value) {
        if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
        values[size++] = value;
    }

    int size() {
        return size;
    }

    void copyTo(float[] target, int offset) {
        System.arraycopy(values, 0, target, offset, size);
    }

    void clear() {
        size = 0;
        if (values.length > RETAINED_CAPACITY) values = new float[INITIAL_CAPACITY];
    }
}
