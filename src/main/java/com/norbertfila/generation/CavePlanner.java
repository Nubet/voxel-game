package com.norbertfila.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.IntBinaryOperator;

final class CavePlanner {
    static final int REGION_SIZE = 128;
    static final int MAX_REACH = 104;
    static final int MAX_SEGMENTS = 512;
    private static final long SALT = 0xA24BAED4963EE407L;
    private final long seed;
    private final int bedrockY;
    private final IntBinaryOperator surfaceHeight;
    private final CaveConfig config;
    private final CaveRegionSampler regions;

    CavePlanner(long seed, int bedrockY, IntBinaryOperator surfaceHeight) {
        this(seed, bedrockY, surfaceHeight, CaveConfig.defaults());
    }

    CavePlanner(long seed, int bedrockY, IntBinaryOperator surfaceHeight, CaveConfig config) {
        this.seed = seed;
        this.bedrockY = bedrockY;
        this.surfaceHeight = Objects.requireNonNull(surfaceHeight);
        this.config = Objects.requireNonNull(config);
        regions = new CaveRegionSampler(seed, config);
    }

    CavePlan plan(int regionX, int regionZ) {
        long ox = (long) regionX * REGION_SIZE, oz = (long) regionZ * REGION_SIZE;
        if (ox < Integer.MIN_VALUE
                || ox + REGION_SIZE - 1 > Integer.MAX_VALUE
                || oz < Integer.MIN_VALUE
                || oz + REGION_SIZE - 1 > Integer.MAX_VALUE) return new CavePlan(List.of());
        var random = new SplittableRandom(GenerationHash.hash(seed ^ SALT, regionX, regionZ));
        var segments = new ArrayList<CavePlan.Segment>();
        var systems = new ArrayList<CaveSystem>();
        double density =
                regions.sample(ox + REGION_SIZE * .5, oz + REGION_SIZE * .5).density();
        for (int attempt = 0; attempt < 4; attempt++) {
            var pathRandom = random.split();
            if (pathRandom.nextDouble() > .15 + density * .8) continue;
            int x = (int) ox + pathRandom.nextInt(REGION_SIZE), z = (int) oz + pathRandom.nextInt(REGION_SIZE);
            long bottom = (long) bedrockY + 12, top = (long) surfaceHeight.applyAsInt(x, z) - 1;
            if (top < bottom) continue;
            double y = bottom + pathRandom.nextLong(top - bottom + 1) + .5;
            double style = pathRandom.nextDouble();
            CaveProfile character = style < .15
                    ? CaveProfile.INTERCONNECTED
                    : style < .35 ? CaveProfile.SPAGHETTI : CaveProfile.CLASSIC;
            var result = new CaveSystemPlanner(pathRandom, config, bedrockY, regions.sample(x, z), surfaceHeight)
                    .plan(x + .5, y, z + .5, character);
            if (segments.size() + result.segments().size() > MAX_SEGMENTS) continue;
            if (result.segments().stream()
                    .anyMatch(s -> s.x() - s.boundRadiusXZ() < ox - MAX_REACH
                            || s.x() + s.boundRadiusXZ() > ox + REGION_SIZE + MAX_REACH
                            || s.z() - s.boundRadiusXZ() < oz - MAX_REACH
                            || s.z() + s.boundRadiusXZ() > oz + REGION_SIZE + MAX_REACH)) continue;
            systems.add(result.system());
            segments.addAll(result.segments());
        }
        return new CavePlan(segments, systems);
    }
}
