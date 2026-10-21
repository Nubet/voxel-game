package com.norbertfila.core;

import com.norbertfila.items.ItemId;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.world.BlockId;
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

    /** The selected item when it can be placed, otherwise air. */
    public byte selectedBlock() {
        byte item = block(selectedSlot);
        return ItemId.isBlock(item) ? item : BlockId.AIR;
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
