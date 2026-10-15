package com.norbertfila.persistence;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WaterState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable set of authoritative block changes inside one chunk. Each change is one int: bits 0-7 hold the block ID,
 * bits 8-15 the water state and bits 16-27 the local index {@code (y * 16 + z) * 16 + x}. Sorting the ints sorts by
 * local index, and equal deltas have equal arrays.
 */
public final class ChunkDelta {
    private final ChunkPos position;
    private final int[] entries;

    private ChunkDelta(ChunkPos position, int[] entries) {
        this.position = Objects.requireNonNull(position);
        if (entries.length == 0 || entries.length > Chunk.BLOCK_BYTES)
            throw new IllegalArgumentException("Chunk delta size is outside the allowed range");
        int previous = -1;
        for (int entry : entries) {
            if (entry >>> 28 != 0) throw new IllegalArgumentException("Invalid packed block change");
            int local = entry >>> 16;
            if (local <= previous) throw new IllegalArgumentException("Delta entries must be unique and sorted");
            previous = local;
            byte value = (byte) entry;
            if (!BlockId.isValid(value)) throw new IllegalArgumentException("Unknown block id");
            WaterState.validate(value, (byte) (entry >>> 8));
        }
        this.entries = entries;
    }

    /** Takes ownership of {@code entries}, which must be sorted packed changes as described on the class. */
    public static ChunkDelta packed(ChunkPos position, int[] entries) {
        return new ChunkDelta(position, entries);
    }

    public ChunkDelta(ChunkPos position, List<BlockChange> changes) {
        this(position, pack(position, changes));
    }

    private static int[] pack(ChunkPos position, List<BlockChange> changes) {
        Objects.requireNonNull(position);
        int[] entries = new int[changes.size()];
        for (int index = 0; index < entries.length; index++) {
            BlockChange change = changes.get(index);
            if (!position.equals(chunkOf(change.position())))
                throw new IllegalArgumentException("Delta block does not belong to its chunk");
            entries[index] = entry(localIndex(change.position()), change.value(), change.waterState());
        }
        Arrays.sort(entries);
        for (int index = 1; index < entries.length; index++)
            if (entries[index] >>> 16 == entries[index - 1] >>> 16)
                throw new IllegalArgumentException("Duplicate delta block");
        return entries;
    }

    public static int entry(int localIndex, byte value, byte waterState) {
        return localIndex << 16 | Byte.toUnsignedInt(waterState) << 8 | BlockId.toUnsignedInt(value);
    }

    private static int localIndex(BlockPos position) {
        int x = Math.floorMod(position.x(), Chunk.SIZE), y = Math.floorMod(position.y(), Chunk.SIZE);
        int z = Math.floorMod(position.z(), Chunk.SIZE);
        return (y * Chunk.SIZE + z) * Chunk.SIZE + x;
    }

    private static ChunkPos chunkOf(BlockPos position) {
        return new ChunkPos(
                Math.floorDiv(position.x(), Chunk.SIZE),
                Math.floorDiv(position.y(), Chunk.SIZE),
                Math.floorDiv(position.z(), Chunk.SIZE));
    }

    public ChunkPos position() {
        return position;
    }

    public int size() {
        return entries.length;
    }

    public int entry(int index) {
        return entries[index];
    }

    public byte value(int index) {
        return (byte) entries[index];
    }

    public byte waterState(int index) {
        return (byte) (entries[index] >>> 8);
    }

    public BlockPos blockPosition(int index) {
        int local = entries[index] >>> 16;
        return new BlockPos(
                position.x() * Chunk.SIZE + (local & 15),
                position.y() * Chunk.SIZE + (local >>> 8),
                position.z() * Chunk.SIZE + (local >>> 4 & 15));
    }

    /** The changes as records, for tools and tests; persistence reads the packed entries directly. */
    public List<BlockChange> changes() {
        List<BlockChange> changes = new ArrayList<>(entries.length);
        for (int index = 0; index < entries.length; index++)
            changes.add(new BlockChange(blockPosition(index), value(index), waterState(index)));
        return List.copyOf(changes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChunkDelta delta
                && position.equals(delta.position)
                && Arrays.equals(entries, delta.entries);
    }

    @Override
    public int hashCode() {
        return 31 * position.hashCode() + Arrays.hashCode(entries);
    }

    @Override
    public String toString() {
        return "ChunkDelta[position=" + position + ", changes=" + entries.length + "]";
    }

    public record BlockChange(BlockPos position, byte value, byte waterState) {
        public BlockChange(BlockPos position, byte value) {
            this(position, value, WaterState.defaultFor(value));
        }

        public BlockChange {
            Objects.requireNonNull(position);
            WaterState.validate(value, waterState);
        }
    }
}
