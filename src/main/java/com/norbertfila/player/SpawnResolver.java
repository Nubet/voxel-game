package com.norbertfila.player;

import com.norbertfila.generation.TerrainSource;
import java.util.Objects;

public final class SpawnResolver {
    private final TerrainSource terrain;

    public SpawnResolver(TerrainSource terrain) {
        this.terrain = Objects.requireNonNull(terrain);
    }

    public PlayerState.Position resolve(int worldX, int worldZ) {
        return new PlayerState.Position(worldX + 0.5, terrain.surfaceHeight(worldX, worldZ) + 1.0, worldZ + 0.5);
    }
}
