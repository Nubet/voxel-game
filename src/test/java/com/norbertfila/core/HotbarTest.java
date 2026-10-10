package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class HotbarTest {
    @Test
    void selectsThreeMaterialsAndSixEmptySlots() {
        Hotbar hotbar = new Hotbar();
        assertEquals(0, hotbar.selectedSlot());
        byte[] expected = {BlockId.GRASS, BlockId.DIRT, BlockId.STONE, 0, 0, 0, 0, 0, 0};
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            hotbar.select(slot);
            assertEquals(slot, hotbar.selectedSlot());
            assertEquals(expected[slot], hotbar.selectedBlock());
            assertEquals(expected[slot], hotbar.block(slot));
        }
        assertThrows(IllegalArgumentException.class, () -> hotbar.select(-1));
        assertThrows(IllegalArgumentException.class, () -> hotbar.select(9));
        assertThrows(IllegalArgumentException.class, () -> hotbar.block(9));
        assertEquals(8, hotbar.selectedSlot());
    }
}
