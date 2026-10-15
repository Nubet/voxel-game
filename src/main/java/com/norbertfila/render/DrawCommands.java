package com.norbertfila.render;

import com.norbertfila.world.ChunkPos;
import java.util.Arrays;

/**
 * One frame of terrain draws in the layout of {@code DrawArraysIndirectCommand}, plus the chunk coordinates each draw
 * reads through its base instance. Draws are grouped into passes, and within a pass into runs that share an arena
 * buffer, so a run can be issued as one multi-draw call. Storage is reused between frames.
 */
final class DrawCommands {
    static final int COMMAND_INTS = 4;
    static final int CHUNK_INTS = 4;

    private int[] commands = new int[COMMAND_INTS * 256];
    private int[] chunks = new int[CHUNK_INTS * 256];
    private int draws;
    private int[] runBuffer = new int[16], runStart = new int[16], runCount = new int[16], runPass = new int[16];
    private int runs;
    private int[] previousCommands = new int[0], previousChunks = new int[0];
    private int previousDraws = -1;

    void clear() {
        draws = 0;
        runs = 0;
    }

    /** Appends a draw of {@code vertexCount} vertices starting at {@code firstVertex} of {@code buffer}. */
    void add(int pass, int buffer, int firstVertex, int vertexCount, ChunkPos chunk) {
        if (vertexCount <= 0 || firstVertex < 0) throw new IllegalArgumentException("Invalid draw range");
        if (runs == 0 || runPass[runs - 1] != pass || runBuffer[runs - 1] != buffer) startRun(pass, buffer);
        commands = grow(commands, (draws + 1) * COMMAND_INTS);
        chunks = grow(chunks, (draws + 1) * CHUNK_INTS);
        int command = draws * COMMAND_INTS;
        commands[command] = vertexCount;
        commands[command + 1] = 1;
        commands[command + 2] = firstVertex;
        commands[command + 3] = draws;
        int coordinates = draws * CHUNK_INTS;
        chunks[coordinates] = chunk.x();
        chunks[coordinates + 1] = chunk.y();
        chunks[coordinates + 2] = chunk.z();
        chunks[coordinates + 3] = 0;
        runCount[runs - 1]++;
        draws++;
    }

    private void startRun(int pass, int buffer) {
        if (runs == runBuffer.length) {
            int capacity = runs * 2;
            runBuffer = Arrays.copyOf(runBuffer, capacity);
            runStart = Arrays.copyOf(runStart, capacity);
            runCount = Arrays.copyOf(runCount, capacity);
            runPass = Arrays.copyOf(runPass, capacity);
        }
        runBuffer[runs] = buffer;
        runStart[runs] = draws;
        runCount[runs] = 0;
        runPass[runs] = pass;
        runs++;
    }

    /**
     * True when this frame differs from the last frame for which this returned true, so GPU copies can be skipped
     * while the visible set and the mesh ranges stay the same.
     */
    boolean changedSinceLastUpload() {
        boolean same = draws == previousDraws
                && Arrays.equals(commands, 0, draws * COMMAND_INTS, previousCommands, 0, draws * COMMAND_INTS)
                && Arrays.equals(chunks, 0, draws * CHUNK_INTS, previousChunks, 0, draws * CHUNK_INTS);
        if (same) return false;
        previousCommands = grow(previousCommands, draws * COMMAND_INTS);
        previousChunks = grow(previousChunks, draws * CHUNK_INTS);
        System.arraycopy(commands, 0, previousCommands, 0, draws * COMMAND_INTS);
        System.arraycopy(chunks, 0, previousChunks, 0, draws * CHUNK_INTS);
        previousDraws = draws;
        return true;
    }

    int draws() {
        return draws;
    }

    int[] commands() {
        return commands;
    }

    int[] chunks() {
        return chunks;
    }

    int runs() {
        return runs;
    }

    int runPass(int run) {
        return runPass[run];
    }

    int runBuffer(int run) {
        return runBuffer[run];
    }

    int runStart(int run) {
        return runStart[run];
    }

    int runCount(int run) {
        return runCount[run];
    }

    private static int[] grow(int[] array, int required) {
        return required <= array.length ? array : Arrays.copyOf(array, Math.max(required, array.length * 2));
    }
}
