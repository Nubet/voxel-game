package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class HotbarTest {
    @Test
    void selectsEveryInitialBlock() {
        Hotbar hotbar = new Hotbar();
        assertEquals(0, hotbar.selectedSlot());
        byte[] expected = {
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
            BlockId.STONE_BRICKS
        };
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            hotbar.select(slot);
            assertEquals(slot, hotbar.selectedSlot());
            assertEquals(expected[slot], hotbar.selectedBlock());
            assertEquals(expected[slot], hotbar.block(slot));
        }
        assertThrows(IllegalArgumentException.class, () -> hotbar.select(-1));
        assertThrows(IllegalArgumentException.class, () -> hotbar.select(15));
        assertThrows(IllegalArgumentException.class, () -> hotbar.block(15));
        assertEquals(14, hotbar.selectedSlot());
    }

    @Test
    void scrollsAcrossTheWholeHotbarAndWrapsInBothDirections() {
        Hotbar hotbar = new Hotbar();
        hotbar.scroll(1);
        assertEquals(14, hotbar.selectedSlot());
        hotbar.scroll(-1);
        assertEquals(0, hotbar.selectedSlot());
        hotbar.scroll(-17);
        assertEquals(2, hotbar.selectedSlot());
    }
}
