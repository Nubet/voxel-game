package com.norbertfila.world;

import java.util.OptionalInt;

public record BlockMutation(
        BlockPos position, OptionalInt previous, OptionalInt previousWaterState, byte block, byte waterState) {}
