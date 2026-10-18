package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.screens.InventoryScreen;
import org.junit.jupiter.api.Test;

class RemovedContentTest {
    @Test
    void removedDripstoneIsNeitherValidNorAvailable() {
        assertFalse(BlockId.isValid((byte) 88));
        assertThrows(IllegalArgumentException.class, () -> BlockRegistry.DEFAULT.definition((byte) 88));
        assertFalse(InventoryScreen.catalogBlocks().contains((byte) 88));
        for (var field : BlockId.class.getFields()) {
            String name = field.getName();
            assertFalse(name.contains("COPPER")
                    || name.contains("RAW_")
                    || name.contains("POWDER")
                    || name.contains("DRIPSTONE"));
        }
    }
}
