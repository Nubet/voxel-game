package com.norbertfila.player;

import com.norbertfila.world.BlockId;
import java.util.Arrays;

public record InventorySnapshot(byte[] blocks, int[] counts, int selectedSlot) {
    public static final InventorySnapshot EMPTY = new InventorySnapshot(new byte[37], new int[37], 0);

    public InventorySnapshot {
        if (blocks.length != 37 || counts.length != 37 || selectedSlot < 0 || selectedSlot >= 9)
            throw new IllegalArgumentException("Invalid inventory dimensions/selection");
        blocks = blocks.clone();
        counts = counts.clone();
        for (int i = 0; i < 37; i++)
            if (!BlockId.isValid(blocks[i])
                    || counts[i] < 0
                    || counts[i] > 64
                    || (blocks[i] == BlockId.AIR) != (counts[i] == 0))
                throw new IllegalArgumentException("Invalid inventory stack");
    }

    @Override
    public byte[] blocks() {
        return blocks.clone();
    }

    @Override
    public int[] counts() {
        return counts.clone();
    }

    public byte block(int slot) {
        return blocks[slot];
    }

    public int count(int slot) {
        return counts[slot];
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof InventorySnapshot other
                && selectedSlot == other.selectedSlot
                && Arrays.equals(blocks, other.blocks)
                && Arrays.equals(counts, other.counts);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(blocks) + Arrays.hashCode(counts)) + selectedSlot;
    }
}
