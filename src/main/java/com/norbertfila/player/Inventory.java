package com.norbertfila.player;

import com.norbertfila.world.BlockId;

public final class Inventory {
    public static final int HOTBAR_SLOTS = 9;
    public static final int SLOTS = 36;
    public static final int CARRIED = 36;
    public static final int MAX_STACK = 64;
    private final byte[] blocks;
    private final int[] counts;
    private int selectedSlot;

    public Inventory() {
        this(InventorySnapshot.EMPTY);
    }

    public Inventory(InventorySnapshot snapshot) {
        blocks = snapshot.blocks();
        counts = snapshot.counts();
        selectedSlot = snapshot.selectedSlot();
    }

    public InventorySnapshot snapshot() {
        return new InventorySnapshot(blocks, counts, selectedSlot);
    }

    public byte selectedBlock() {
        return blocks[selectedSlot];
    }

    public void select(int slot) {
        checkHotbar(slot);
        selectedSlot = slot;
    }

    public void creativePick(int slot, byte block) {
        checkHotbar(slot);
        if (!BlockId.isValid(block) || block == BlockId.AIR)
            throw new IllegalArgumentException("Invalid catalog block");
        blocks[slot] = block;
        counts[slot] = MAX_STACK;
    }

    public boolean add(byte block) {
        return insert(BlockId.toUnsignedInt(block), 1) == 0;
    }

    public int insert(int itemId, int count) {
        if (itemId <= 0 || itemId > 255 || !BlockId.isValid((byte) itemId) || count < 0)
            throw new IllegalArgumentException("Invalid insertion");
        byte block = (byte) itemId;
        for (int pass = 0; pass < 2 && count > 0; pass++)
            for (int i = 0; i < SLOTS && count > 0; i++)
                if (pass == 0 ? blocks[i] == block && counts[i] < MAX_STACK : counts[i] == 0) {
                    int move = Math.min(count, MAX_STACK - counts[i]);
                    blocks[i] = block;
                    counts[i] += move;
                    count -= move;
                }
        return count;
    }

    public void consumeSelected() {
        if (counts[selectedSlot] == 0) throw new IllegalStateException("Cannot consume empty slot");
        if (--counts[selectedSlot] == 0) blocks[selectedSlot] = BlockId.AIR;
    }

    public void click(int slot, boolean right, boolean quickMove) {
        checkSlot(slot);
        if (quickMove) {
            if (counts[CARRIED] != 0) return;
            transfer(slot, slot < HOTBAR_SLOTS ? HOTBAR_SLOTS : 0, slot < HOTBAR_SLOTS ? SLOTS : HOTBAR_SLOTS);
            return;
        }
        if (counts[CARRIED] == 0) {
            int take = right ? (counts[slot] + 1) / 2 : counts[slot];
            blocks[CARRIED] = blocks[slot];
            counts[CARRIED] = take;
            counts[slot] -= take;
            clearEmpty(slot);
        } else if (counts[slot] == 0 || blocks[slot] == blocks[CARRIED]) {
            int move = Math.min(right ? 1 : counts[CARRIED], MAX_STACK - counts[slot]);
            if (move == 0) return;
            blocks[slot] = blocks[CARRIED];
            counts[slot] += move;
            counts[CARRIED] -= move;
            clearEmpty(CARRIED);
        } else {
            byte block = blocks[slot];
            int count = counts[slot];
            blocks[slot] = blocks[CARRIED];
            counts[slot] = counts[CARRIED];
            blocks[CARRIED] = block;
            counts[CARRIED] = count;
        }
    }

    public void returnCarried() {
        // Overflow stays on the persisted cursor so closing a full inventory cannot discard it.
        transfer(CARRIED, 0, SLOTS);
    }

    private void transfer(int source, int first, int end) {
        for (int pass = 0; pass < 2 && counts[source] > 0; pass++)
            for (int i = first; i < end && counts[source] > 0; i++) {
                if (i == source || (pass == 0 ? blocks[i] != blocks[source] : counts[i] != 0)) continue;
                int move = Math.min(counts[source], MAX_STACK - counts[i]);
                if (move == 0) continue;
                blocks[i] = blocks[source];
                counts[i] += move;
                counts[source] -= move;
            }
        clearEmpty(source);
    }

    private void clearEmpty(int slot) {
        if (counts[slot] == 0) blocks[slot] = BlockId.AIR;
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Invalid inventory slot");
    }

    private static void checkHotbar(int slot) {
        if (slot < 0 || slot >= HOTBAR_SLOTS) throw new IllegalArgumentException("Invalid hotbar slot");
    }
}
