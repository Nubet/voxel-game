package com.norbertfila.core;

import com.norbertfila.player.InventorySnapshot;
import java.util.Objects;

public final class Hotbar {
    public static final int SLOTS = 9;
    private InventorySnapshot inventory = InventorySnapshot.EMPTY;
    private int selectedSlot;

    public void inventory(InventorySnapshot snapshot) {
        inventory = Objects.requireNonNull(snapshot);
    }

    public int count(int slot) {
        checkSlot(slot);
        return inventory.count(slot);
    }

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
        return inventory.block(slot);
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS)
            throw new IllegalArgumentException("Hotbar slot must be in [0, " + (SLOTS - 1) + "]");
    }
}
