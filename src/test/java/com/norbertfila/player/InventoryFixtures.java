package com.norbertfila.player;

import com.norbertfila.core.Hotbar;
import com.norbertfila.world.BlockId;

public final class InventoryFixtures {
    public static Hotbar hotbar() {
        byte[] blocks = new byte[37];
        int[] counts = new int[37];
        byte[] palette = {
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
        System.arraycopy(palette, 0, blocks, 0, 9);
        java.util.Arrays.fill(counts, 0, 9, 1);
        Hotbar hotbar = new Hotbar();
        hotbar.inventory(new InventorySnapshot(blocks, counts, 0));
        return hotbar;
    }

    private InventoryFixtures() {}
}
