package com.norbertfila.vegetation;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.World;

public interface GrowthRule {
    byte blockId();

    long delay(long seed, BlockPos root, long startedAt, long cycle);

    GrowthSite inspect(World world, BlockPos root);
}
