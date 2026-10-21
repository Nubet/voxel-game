package com.norbertfila.explosion;

import com.norbertfila.world.BlockPos;
import java.util.List;

/** Removes blocks through the game's normal edit path, so that meshes, fluids and plants react as to any edit. */
@FunctionalInterface
public interface BlockSink {
    void destroy(List<BlockPos> blocks);
}
