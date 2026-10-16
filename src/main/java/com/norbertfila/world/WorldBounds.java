package com.norbertfila.world;

public final class WorldBounds {
    public static final int MIN_Y = -64;
    public static final int MAX_Y_EXCLUSIVE = 256;
    public static final int MIN_CHUNK_Y = Math.floorDiv(MIN_Y, Chunk.SIZE);
    public static final int MAX_CHUNK_Y = Math.floorDiv(MAX_Y_EXCLUSIVE - 1, Chunk.SIZE);

    private WorldBounds() {}

    public static boolean containsY(int y) {
        return y >= MIN_Y && y < MAX_Y_EXCLUSIVE;
    }
}
