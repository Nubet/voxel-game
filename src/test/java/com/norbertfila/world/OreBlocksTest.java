package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.BlockDropResolver;
import com.norbertfila.items.ItemStack;
import com.norbertfila.items.ToolContext;
import com.norbertfila.ui.screens.InventoryScreen;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OreBlocksTest {
    @ParameterizedTest
    @ValueSource(ints = {42, 43, 44, 45, 46})
    void oresAreSolidCreativeBlocksWithSelfDrops(int id) {
        assertTrue(BlockId.isValid((byte) id));
        var definition = BlockRegistry.DEFAULT.definition((byte) id);
        assertEquals(BlockShape.CUBE, definition.shape());
        assertEquals(RenderLayer.OPAQUE, definition.renderLayer());
        assertTrue(definition.collidable());
        assertTrue(definition.occludesFullFace());
        assertTrue(definition.breakable());
        assertEquals(1, definition.breakSeconds());
        assertTrue(InventoryScreen.catalogBlocks().contains((byte) id));
        assertEquals(List.of(new ItemStack(id, 1)), new BlockDropResolver().getDrops(id, ToolContext.HAND));
    }
}
