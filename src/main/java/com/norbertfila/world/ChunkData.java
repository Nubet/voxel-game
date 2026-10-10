package com.norbertfila.world;

import java.util.Objects;

public sealed interface ChunkData permits ChunkData.Empty, ChunkData.Uniform, ChunkData.Dense {
    enum Kind {
        EMPTY,
        UNIFORM,
        DENSE
    }

    byte get(int index);

    ChunkData set(int index, byte value);

    byte[] toBlocks();

    Kind kind();

    long estimatedBytes();

    int length();

    static ChunkData empty() {
        return empty(Chunk.BLOCK_BYTES);
    }

    static ChunkData empty(int length) {
        checkLength(length);
        return new Empty(length);
    }

    static ChunkData fromBlocks(byte[] source) {
        Objects.requireNonNull(source);
        checkLength(source.length);
        byte first = source[0];
        boolean uniform = true;
        for (byte value : source) {
            if (!BlockId.isValid(value))
                throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
            uniform &= value == first;
        }
        if (uniform) return first == BlockId.AIR ? new Empty(source.length) : new Uniform(first, source.length);
        return new Dense(source);
    }

    final class Empty implements ChunkData {
        private final int length;

        Empty(int length) {
            this.length = length;
        }

        @Override
        public byte get(int index) {
            checkIndex(index, length);
            return BlockId.AIR;
        }

        @Override
        public ChunkData set(int index, byte value) {
            checkIndex(index, length);
            checkValue(value);
            return value == BlockId.AIR ? this : Dense.withValue(index, value, BlockId.AIR, length);
        }

        @Override
        public byte[] toBlocks() {
            return new byte[length];
        }

        @Override
        public Kind kind() {
            return Kind.EMPTY;
        }

        @Override
        public long estimatedBytes() {
            return 0;
        }

        @Override
        public int length() {
            return length;
        }
    }

    record Uniform(byte value, int length) implements ChunkData {
        public Uniform(byte value) {
            this(value, Chunk.BLOCK_BYTES);
        }

        public Uniform {
            if (!BlockId.isValid(value) || value == BlockId.AIR)
                throw new IllegalArgumentException("Invalid uniform block id");
            checkLength(length);
        }

        @Override
        public byte get(int index) {
            checkIndex(index, length);
            return value;
        }

        @Override
        public ChunkData set(int index, byte next) {
            checkIndex(index, length);
            checkValue(next);
            return value == next ? this : Dense.withValue(index, next, value, length);
        }

        @Override
        public byte[] toBlocks() {
            byte[] blocks = new byte[length];
            java.util.Arrays.fill(blocks, value);
            return blocks;
        }

        @Override
        public Kind kind() {
            return Kind.UNIFORM;
        }

        @Override
        public long estimatedBytes() {
            return 1;
        }
    }

    final class Dense implements ChunkData {
        private final byte[] blocks;

        Dense(byte[] blocks) {
            this.blocks = blocks.clone();
        }

        static Dense withValue(int index, byte value, byte fill, int length) {
            byte[] blocks = new byte[length];
            java.util.Arrays.fill(blocks, fill);
            blocks[index] = value;
            return new Dense(blocks);
        }

        @Override
        public byte get(int index) {
            checkIndex(index, blocks.length);
            return blocks[index];
        }

        @Override
        public ChunkData set(int index, byte value) {
            checkIndex(index, blocks.length);
            checkValue(value);
            blocks[index] = value;
            return this;
        }

        @Override
        public byte[] toBlocks() {
            return blocks.clone();
        }

        @Override
        public Kind kind() {
            return Kind.DENSE;
        }

        @Override
        public long estimatedBytes() {
            return blocks.length;
        }

        @Override
        public int length() {
            return blocks.length;
        }
    }

    private static void checkIndex(int index, int length) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException("Chunk index: " + index);
    }

    private static void checkLength(int length) {
        if (length <= 0) throw new IllegalArgumentException("Invalid storage length: " + length);
    }

    private static void checkValue(byte value) {
        if (!BlockId.isValid(value))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
    }
}
