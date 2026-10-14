package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.player.Inventory;
import com.norbertfila.player.InventoryFixtures;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class HotbarTest {
    @Test
    void selectsEveryPublishedBlock() {
        Hotbar hotbar = InventoryFixtures.hotbar();
        assertEquals(0, hotbar.selectedSlot());
        byte[] expected = {
            BlockId.GRASS,
            BlockId.DIRT,
            BlockId.STONE,
            BlockId.SAND,
            BlockId.LOG,
            BlockId.LEAVES,
            BlockId.PLANKS,
            BlockId.GLASS,
            BlockId.WATER
        };
        assertEquals(9, Hotbar.SLOTS);
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            hotbar.select(slot);
            assertEquals(slot, hotbar.selectedSlot());
            assertEquals(expected[slot], hotbar.selectedBlock());
            assertEquals(expected[slot], hotbar.block(slot));
        }
        assertThrows(IllegalArgumentException.class, () -> hotbar.select(-1));
        assertThrows(IllegalArgumentException.class, () -> hotbar.select(Hotbar.SLOTS));
        assertThrows(IllegalArgumentException.class, () -> hotbar.block(Hotbar.SLOTS));
        assertEquals(8, hotbar.selectedSlot());
    }

    @Test
    void startsEmptyAndPublishedContentsDoNotUndoLocalSelection() {
        Hotbar hotbar = new Hotbar();
        assertEquals(BlockId.AIR, hotbar.selectedBlock());
        assertEquals(0, hotbar.count(0));
        hotbar.select(8);
        var inventory = new Inventory();
        inventory.creativePick(8, BlockId.POPPY);
        hotbar.inventory(inventory.snapshot());
        assertEquals(8, hotbar.selectedSlot());
        assertEquals(BlockId.POPPY, hotbar.selectedBlock());
        assertEquals(64, hotbar.count(8));
    }

    @Test
    void scrollsAcrossTheWholeHotbarAndWrapsInBothDirections() {
        Hotbar hotbar = new Hotbar();
        hotbar.scroll(1);
        assertEquals(8, hotbar.selectedSlot());
        hotbar.scroll(-1);
        assertEquals(0, hotbar.selectedSlot());
        hotbar.scroll(-10);
        assertEquals(1, hotbar.selectedSlot());
        hotbar.scroll(Integer.MIN_VALUE);
        assertEquals((int) Math.floorMod(1L - Integer.MIN_VALUE, 9), hotbar.selectedSlot());
    }
}
