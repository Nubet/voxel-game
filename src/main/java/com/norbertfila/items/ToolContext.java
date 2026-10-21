package com.norbertfila.items;

import com.norbertfila.world.BlockId;
import java.util.Optional;

/** The item held while breaking a block. */
public record ToolContext(byte item) {
    public static final ToolContext HAND = new ToolContext(BlockId.AIR);

    public ToolContext {
        if (!ItemId.isValid(item)) throw new IllegalArgumentException("Unknown item id: " + Byte.toUnsignedInt(item));
    }

    public Optional<Tool> tool() {
        return Tool.of(item);
    }
}
