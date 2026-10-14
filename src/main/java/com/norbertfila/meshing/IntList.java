package com.norbertfila.meshing;

import java.util.Arrays;

/** Growable int buffer reused across meshing calls on one worker thread. */
final class IntList {
    // Buffers that grew past this size after a pathological mesh are dropped so a worker does not keep them.
    private static final int RETAINED_CAPACITY = 1 << 16;
    private static final int INITIAL_CAPACITY = 1024;
    private int[] values = new int[INITIAL_CAPACITY];
    private int size;

    void add(int value) {
        if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
        values[size++] = value;
    }

    int size() {
        return size;
    }

    void copyTo(int[] target, int offset) {
        System.arraycopy(values, 0, target, offset, size);
    }

    void clear() {
        size = 0;
        if (values.length > RETAINED_CAPACITY) values = new int[INITIAL_CAPACITY];
    }
}
