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

    static ChunkData empty() {
        return new Empty();
    }

    static ChunkData fromBlocks(byte[] source) {
        Objects.requireNonNull(source);
        if (source.length != Chunk.BLOCK_BYTES)
            throw new IllegalArgumentException("Invalid chunk data size: " + source.length);
        byte first = source[0];
        boolean uniform = true;
        for (byte value : source) {
            if (!BlockId.isValid(value))
                throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
            uniform &= value == first;
        }
        if (uniform) return first == BlockId.AIR ? new Empty() : new Uniform(first);
        return new Dense(source);
    }

    final class Empty implements ChunkData {
        @Override
        public byte get(int index) {
            checkIndex(index);
            return BlockId.AIR;
        }

        @Override
        public ChunkData set(int index, byte value) {
            checkIndex(index);
            checkValue(value);
            return value == BlockId.AIR ? this : Dense.withValue(index, value);
        }

        @Override
        public byte[] toBlocks() {
            return new byte[Chunk.BLOCK_BYTES];
        }

        @Override
        public Kind kind() {
            return Kind.EMPTY;
        }

        @Override
        public long estimatedBytes() {
            return 0;
        }
    }

    record Uniform(byte value) implements ChunkData {
        public Uniform {
            if (!BlockId.isValid(value) || value == BlockId.AIR)
                throw new IllegalArgumentException("Invalid uniform block id");
        }

        @Override
        public byte get(int index) {
            checkIndex(index);
            return value;
        }

        @Override
        public ChunkData set(int index, byte next) {
            checkIndex(index);
            checkValue(next);
            return value == next ? this : Dense.withValue(index, next, value);
        }

        @Override
        public byte[] toBlocks() {
            byte[] blocks = new byte[Chunk.BLOCK_BYTES];
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

        static Dense withValue(int index, byte value) {
            return withValue(index, value, BlockId.AIR);
        }

        static Dense withValue(int index, byte value, byte fill) {
            byte[] blocks = new byte[Chunk.BLOCK_BYTES];
            java.util.Arrays.fill(blocks, fill);
            blocks[index] = value;
            return new Dense(blocks);
        }

        @Override
        public byte get(int index) {
            checkIndex(index);
            return blocks[index];
        }

        @Override
        public ChunkData set(int index, byte value) {
            checkIndex(index);
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
            return Chunk.BLOCK_BYTES;
        }
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= Chunk.BLOCK_BYTES) throw new IndexOutOfBoundsException("Chunk index: " + index);
    }

    private static void checkValue(byte value) {
        if (!BlockId.isValid(value))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
    }
}
