package com.norbertfila.items;

import com.norbertfila.player.Inventory;
import com.norbertfila.world.BlockId;

public record ItemStack(int itemId, int count) {
    public ItemStack {
        if (itemId <= 0 || itemId > 255 || !BlockId.isValid((byte) itemId) || count < 1 || count > Inventory.MAX_STACK)
            throw new IllegalArgumentException("Invalid item stack");
    }
}
