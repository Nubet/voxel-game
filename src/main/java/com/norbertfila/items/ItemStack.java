package com.norbertfila.items;

public record ItemStack(int itemId, int count) {
    public ItemStack {
        if (itemId <= 0
                || itemId > 255
                || !ItemId.isValid((byte) itemId)
                || count < 1
                || count > ItemId.maxStack((byte) itemId)) throw new IllegalArgumentException("Invalid item stack");
    }
}
