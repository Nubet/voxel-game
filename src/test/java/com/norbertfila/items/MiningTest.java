package com.norbertfila.items;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiningTest {
    private static ToolContext held(Tool tool) {
        return new ToolContext(tool.id());
    }

    @Test
    void matchingToolsDivideTheHandTimeByTheirTierSpeed() {
        assertEquals(.5, Mining.seconds(BlockId.STONE, held(Tool.WOODEN_PICKAXE)), 1e-9);
        assertEquals(.125, Mining.seconds(BlockId.STONE, held(Tool.DIAMOND_PICKAXE)), 1e-9);
        assertEquals(1 / 6d, Mining.seconds(BlockId.LOG, held(Tool.IRON_AXE)), 1e-9);
        assertEquals(.25, Mining.seconds(BlockId.BIRCH_PLANKS, held(Tool.STONE_AXE)), 1e-9);
        assertEquals(1, Mining.seconds(BlockId.LOG, held(Tool.DIAMOND_PICKAXE)), 1e-9);
        assertEquals(1, Mining.seconds(BlockId.DIRT, held(Tool.DIAMOND_AXE)), 1e-9);
        assertEquals(.2, Mining.seconds(BlockId.LEAVES, held(Tool.WOODEN_SWORD)), 1e-9);
        assertEquals(0, Mining.seconds(BlockId.POPPY, ToolContext.HAND));
    }

    @Test
    void stoneLikeBlocksNeedAPickaxeOfTheRightTierToDrop() {
        var drops = new BlockDropResolver();
        assertEquals(10 / 3d, Mining.seconds(BlockId.STONE, ToolContext.HAND), 1e-9);
        assertEquals(List.of(), drops.getDrops(BlockId.STONE, ToolContext.HAND));
        assertEquals(List.of(), drops.getDrops(BlockId.COBBLE, held(Tool.DIAMOND_SWORD)));
        assertEquals(
                List.of(new ItemStack(BlockId.COBBLE, 1)), drops.getDrops(BlockId.STONE, held(Tool.WOODEN_PICKAXE)));
        assertFalse(Mining.canHarvest(BlockId.IRON_ORE, held(Tool.WOODEN_PICKAXE)));
        assertTrue(Mining.canHarvest(BlockId.IRON_ORE, held(Tool.STONE_PICKAXE)));
        assertFalse(Mining.canHarvest(BlockId.DIAMOND_ORE, held(Tool.STONE_PICKAXE)));
        assertTrue(Mining.canHarvest(BlockId.DIAMOND_ORE, held(Tool.IRON_PICKAXE)));
        assertEquals(10 / 3d / 4, Mining.seconds(BlockId.GOLD_ORE, held(Tool.STONE_PICKAXE)), 1e-9);
        for (byte block : new byte[] {BlockId.DIRT, BlockId.LOG, BlockId.GLASS, BlockId.ICE})
            assertTrue(Mining.canHarvest(block, ToolContext.HAND));
        for (byte block : new byte[] {BlockId.SANDSTONE, BlockId.TERRACOTTA, BlockId.CONCRETE_RED, BlockId.TUFF_BRICKS})
            assertFalse(Mining.canHarvest(block, ToolContext.HAND));
    }
}
