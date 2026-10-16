package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.WorldBounds;
import java.util.Objects;
import java.util.random.RandomGenerator;

record OreDefinition(
        byte block, int minY, int maxY, int attempts, int size, HeightDistribution distribution, long salt) {
    OreDefinition {
        if (!BlockId.isValid(block) || block == BlockId.AIR || block == BlockId.STONE)
            throw new IllegalArgumentException("Ore must be a valid replacement block");
        if (!WorldBounds.containsY(minY) || !WorldBounds.containsY(maxY) || minY > maxY)
            throw new IllegalArgumentException("Ore heights must be ordered and inside the world");
        if (attempts < 1 || size < 1 || size > 32)
            throw new IllegalArgumentException("Ore attempts must be positive and vein size in [1, 32]");
        Objects.requireNonNull(distribution);
    }

    int height(RandomGenerator random) {
        int range = maxY - minY + 1;
        if (distribution == HeightDistribution.UNIFORM) return minY + random.nextInt(range);
        return minY + (random.nextInt(range) + random.nextInt(range)) / 2;
    }

    enum HeightDistribution {
        UNIFORM,
        TRIANGULAR
    }
}
