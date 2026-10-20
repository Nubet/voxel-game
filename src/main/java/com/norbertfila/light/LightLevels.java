package com.norbertfila.light;

import com.norbertfila.world.BlockPos;
import java.util.OptionalInt;

/** Light levels in [0, 15]; empty when the answer depends on chunks that are not loaded. */
public interface LightLevels {
    int MAX_LEVEL = 15;

    OptionalInt skyLight(BlockPos position);

    OptionalInt blockLight(BlockPos position);
}
