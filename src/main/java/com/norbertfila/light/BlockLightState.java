package com.norbertfila.light;

import java.util.Objects;

/** Light from placed emitters and from what the player carries; the brighter of the two wins in each cell. */
public record BlockLightState(BlockLightVolume placed, BlockLightVolume held) {
    public static final BlockLightState NONE = new BlockLightState(BlockLightVolume.EMPTY, BlockLightVolume.EMPTY);

    public BlockLightState {
        Objects.requireNonNull(placed);
        Objects.requireNonNull(held);
    }

    public int level(int x, int y, int z) {
        return Math.max(placed.level(x, y, z), held.level(x, y, z));
    }
}
