package com.norbertfila.items;

import com.norbertfila.world.BlockId;
import java.util.List;
import java.util.Objects;

public final class BlockDropResolver {
    public List<ItemStack> getDrops(int blockId, ToolContext tool) {
        Objects.requireNonNull(tool);
        if (blockId < 0 || blockId > 255 || !BlockId.isValid((byte) blockId))
            throw new IllegalArgumentException("Unknown block");
        return switch (blockId) {
            case BlockId.AIR, BlockId.WATER, BlockId.BEDROCK -> List.of();
            case BlockId.STONE -> List.of(new ItemStack(BlockId.COBBLE, 1));
            case BlockId.GRASS -> List.of(new ItemStack(BlockId.DIRT, 1));
            default -> List.of(new ItemStack(blockId, 1));
        };
    }
}
