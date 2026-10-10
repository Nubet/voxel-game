package com.norbertfila.world;

import java.util.Arrays;

public final class LayerStorage {
    public static final int LAYER_BLOCKS = Chunk.SIZE * Chunk.SIZE;
    private final ChunkData[] layers = new ChunkData[Chunk.SIZE];

    public LayerStorage() {
        Arrays.fill(layers, ChunkData.empty(LAYER_BLOCKS));
    }

    public static LayerStorage fromBlocks(byte[] source) {
        if (source.length != Chunk.BLOCK_BYTES)
            throw new IllegalArgumentException("Invalid chunk data size: " + source.length);
        LayerStorage storage = new LayerStorage();
        for (int y = 0; y < Chunk.SIZE; y++) {
            storage.layers[y] =
                    ChunkData.fromBlocks(Arrays.copyOfRange(source, y * LAYER_BLOCKS, (y + 1) * LAYER_BLOCKS));
        }
        return storage;
    }

    public byte get(int x, int y, int z) {
        checkHorizontalCoordinate(x, "x");
        checkHorizontalCoordinate(z, "z");
        return layer(y).get(z * Chunk.SIZE + x);
    }

    public void set(int x, int y, int z, byte value) {
        checkHorizontalCoordinate(x, "x");
        checkHorizontalCoordinate(z, "z");
        int index = z * Chunk.SIZE + x;
        ChunkData layer = layer(y);
        if (layer.get(index) != value) layers[y] = layer.set(index, value);
    }

    public byte[] toBlocks() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        for (int y = 0; y < Chunk.SIZE; y++) {
            System.arraycopy(layers[y].toBlocks(), 0, blocks, y * LAYER_BLOCKS, LAYER_BLOCKS);
        }
        return blocks;
    }

    public long estimatedBytes() {
        long bytes = 0;
        for (ChunkData layer : layers) bytes += layer.estimatedBytes();
        return bytes;
    }

    public ChunkData.Kind kind() {
        ChunkData.Kind kind = layers[0].kind();
        if (kind == ChunkData.Kind.EMPTY) {
            for (int y = 1; y < Chunk.SIZE; y++)
                if (layers[y].kind() != ChunkData.Kind.EMPTY) return ChunkData.Kind.DENSE;
            return kind;
        }
        if (kind == ChunkData.Kind.UNIFORM) {
            byte value = layers[0].get(0);
            for (int y = 1; y < Chunk.SIZE; y++) {
                if (layers[y].kind() != ChunkData.Kind.UNIFORM || layers[y].get(0) != value)
                    return ChunkData.Kind.DENSE;
            }
            return kind;
        }
        return kind;
    }

    private ChunkData layer(int y) {
        if (y < 0 || y >= Chunk.SIZE) throw new IndexOutOfBoundsException("y must be in [0, " + Chunk.SIZE + "): " + y);
        return layers[y];
    }

    private static void checkHorizontalCoordinate(int coordinate, String axis) {
        if (coordinate < 0 || coordinate >= Chunk.SIZE) {
            throw new IndexOutOfBoundsException(axis + " must be in [0, " + Chunk.SIZE + "): " + coordinate);
        }
    }
}
