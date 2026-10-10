package com.norbertfila.player;

import com.norbertfila.generation.TerrainSource;
import java.util.Objects;

public final class SpawnResolver {
    private final TerrainSource terrain;

    public SpawnResolver(TerrainSource terrain) {
        this.terrain = Objects.requireNonNull(terrain);
    }

    public PlayerController.Position resolve(int worldX, int worldZ) {
        return new PlayerController.Position(
                worldX + 0.5, terrain.sample(worldX, worldZ).surfaceHeight() + 1.0, worldZ + 0.5);
    }
}
