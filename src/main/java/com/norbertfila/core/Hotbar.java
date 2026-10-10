package com.norbertfila.core;

import com.norbertfila.world.BlockId;

public final class Hotbar {
    public static final int SLOTS = 9;
    private int selectedSlot;

    public int selectedSlot() {
        return selectedSlot;
    }

    public void select(int slot) {
        checkSlot(slot);
        selectedSlot = slot;
    }

    public byte selectedBlock() {
        return block(selectedSlot);
    }

    public byte block(int slot) {
        checkSlot(slot);
        return switch (slot) {
            case 0 -> BlockId.GRASS;
            case 1 -> BlockId.DIRT;
            case 2 -> BlockId.STONE;
            default -> BlockId.AIR;
        };
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Hotbar slot must be in [0, 8]");
    }
}
