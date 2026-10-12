package com.norbertfila.core;

import com.norbertfila.world.BlockId;

public final class Hotbar {
    private static final byte[] BLOCKS = {
        BlockId.GRASS,
        BlockId.DIRT,
        BlockId.STONE,
        BlockId.SAND,
        BlockId.GRAVEL,
        BlockId.BEDROCK,
        BlockId.SNOW,
        BlockId.LOG,
        BlockId.LEAVES,
        BlockId.PLANKS,
        BlockId.COBBLE,
        BlockId.BRICKS,
        BlockId.GLASS,
        BlockId.SANDSTONE,
        BlockId.STONE_BRICKS,
        BlockId.WATER
    };
    public static final int SLOTS = BLOCKS.length;
    private int selectedSlot;

    public int selectedSlot() {
        return selectedSlot;
    }

    public void select(int slot) {
        checkSlot(slot);
        selectedSlot = slot;
    }

    public void scroll(int steps) {
        selectedSlot = Math.floorMod(selectedSlot - Math.floorMod(steps, SLOTS), SLOTS);
    }

    public byte selectedBlock() {
        return block(selectedSlot);
    }

    public byte block(int slot) {
        checkSlot(slot);
        return BLOCKS[slot];
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS)
            throw new IllegalArgumentException("Hotbar slot must be in [0, " + (SLOTS - 1) + "]");
    }
}
