package com.norbertfila.light;

import com.norbertfila.world.BlockPos;
import java.util.Objects;
import java.util.OptionalInt;

public record LightSample(OptionalInt sky, OptionalInt block) {
    public static final LightSample UNKNOWN = new LightSample(OptionalInt.empty(), OptionalInt.empty());

    public LightSample {
        Objects.requireNonNull(sky);
        Objects.requireNonNull(block);
    }

    public static LightSample at(LightLevels light, BlockPos position) {
        return new LightSample(light.skyLight(position), light.blockLight(position));
    }
}
