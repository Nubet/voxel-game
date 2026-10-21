package com.norbertfila.mob.ai.navigation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldBounds;
import java.util.Objects;

public final class WorldPathTerrain implements PathTerrain {
    static final double STEP_HEIGHT = .6;
    private final World world;

    public WorldPathTerrain(World world) {
        this.world = Objects.requireNonNull(world);
    }

    @Override
    public Cell cell(int x, int y, int z) {
        if (!WorldBounds.containsY(y)) return y < WorldBounds.MIN_Y ? Cell.SOLID : Cell.OPEN;
        var block = world.getBlock(new BlockPos(x, y, z));
        if (block.isEmpty()) return Cell.UNKNOWN;
        int id = block.getAsInt();
        if (id == Byte.toUnsignedInt(BlockId.LAVA)) return Cell.DANGER;
        if (id == BlockId.WATER) return Cell.WATER;
        var definition = BlockRegistry.DEFAULT.definition((byte) id);
        // Low blocks such as snow layers are stepped over like air, matching the mob step height.
        return definition.collidable() && definition.model().maxY() > STEP_HEIGHT ? Cell.SOLID : Cell.OPEN;
    }
}
