package com.norbertfila.items;

import com.norbertfila.player.Inventory;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import java.util.Optional;

/** Item ids share the unsigned byte space with block ids; ids without a block are tools. */
public final class ItemId {
    private ItemId() {}

    public static boolean isValid(byte id) {
        return BlockId.isValid(id) || Tool.of(id).isPresent();
    }

    public static boolean isBlock(byte id) {
        return BlockId.isValid(id);
    }

    public static int maxStack(byte id) {
        return Tool.of(id).isPresent() ? 1 : Inventory.MAX_STACK;
    }

    public static String name(byte id) {
        return Tool.of(id)
                .map(Tool::itemName)
                .orElseGet(() -> BlockRegistry.DEFAULT.definition(id).name());
    }

    public static String translationKey(byte id) {
        return (isBlock(id) ? "block." : "item.") + name(id);
    }

    public static Optional<Byte> byName(String name) {
        for (int id = 1; id <= 255; id++)
            if (isValid((byte) id) && name((byte) id).equals(name)) return Optional.of((byte) id);
        return Optional.empty();
    }
}
