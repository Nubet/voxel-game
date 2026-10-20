package com.norbertfila.vegetation;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.Set;

public interface GrowthRule {
    byte blockId();

    long delay(long seed, BlockPos root, long startedAt, long cycle);

    GrowthSite inspect(World world, BlockPos root);

    Set<ChunkPos> dependencies(BlockPos root);
}
