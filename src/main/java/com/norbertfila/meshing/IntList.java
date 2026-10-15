package com.norbertfila.meshing;

import java.util.Arrays;

/** Growable int buffer, reused across meshing calls on one worker thread where possible. */
public final class IntList {
    // Buffers that grew past this size after a pathological mesh are dropped so a worker does not keep them.
    private static final int RETAINED_CAPACITY = 1 << 16;
    private static final int INITIAL_CAPACITY = 1024;
    private int[] values = new int[INITIAL_CAPACITY];
    private int size;

    public void add(int value) {
        if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
        values[size++] = value;
    }

    public int size() {
        return size;
    }

    public void copyTo(int[] target, int offset) {
        System.arraycopy(values, 0, target, offset, size);
    }

    public int[] toArray() {
        return Arrays.copyOf(values, size);
    }

    public void clear() {
        size = 0;
        if (values.length > RETAINED_CAPACITY) values = new int[INITIAL_CAPACITY];
    }
}
