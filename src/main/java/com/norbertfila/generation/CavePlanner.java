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
        var regional = regions.sample(ox + REGION_SIZE * .5, oz + REGION_SIZE * .5);
        int systems = 2 + (int) Math.round(regional.density() * 4);
        for (int system = 0; system < systems; system++) {
            var pathRandom = random.split();
            int x = (int) ox + pathRandom.nextInt(REGION_SIZE);
            int z = (int) oz + pathRandom.nextInt(REGION_SIZE);
            long bottom = (long) bedrockY + 5, top = (long) surfaceHeight.applyAsInt(x, z) - 1;
            if (top < bottom) continue;
            double y = bottom + pathRandom.nextLong(top - bottom + 1) + .5;
            int budget = Math.min(128, (MAX_SEGMENTS - segments.size()) / (systems - system));
            var local = regions.sample(x, z);
            double radius = config.minRadius() + local.width() * (config.maxRadius() - config.minRadius());
            if (pathRandom.nextDouble() < local.chamberChance()) {
                double chamber = pathRandom.nextDouble(4, 6);
                segments.add(new CavePlan.Segment(x + .5, y, z + .5, chamber, chamber * .6));
                budget--;
            }
            tunnel(
                    segments,
                    pathRandom,
                    x + .5,
                    y,
                    z + .5,
                    pathRandom.nextDouble(Math.PI * 2),
                    pathRandom.nextDouble(-.12, .12),
                    radius,
                    pathRandom.nextInt(config.minLength(), config.maxLength() + 1),
                    budget,
                    true);
        }
        return new CavePlan(segments);
    }

    private void tunnel(
            List<CavePlan.Segment> segments,
            SplittableRandom random,
            double x,
            double y,
            double z,
            double yaw,
            double pitch,
            double width,
            int steps,
            int budget,
            boolean branching) {
        int limit = Math.min(MAX_SEGMENTS, segments.size() + budget);
        int branch = steps / 3 + random.nextInt(Math.max(1, steps / 3));
        double yawDrift = 0, pitchDrift = 0;
        for (int step = 0; step < steps && segments.size() < limit; step++) {
            var local = regions.sample(x, z);
            double radius = Math.clamp(width + Math.sin(step * .17) * .18, config.minRadius(), config.maxRadius());
            segments.add(new CavePlan.Segment(x, y, z, radius, Math.max(1.85, radius * .8)));
            if (branching && step == branch && random.nextDouble() < local.branchChance()) {
                int remaining = steps - step - 1;
                int childSteps = Math.min(20, remaining);
                int spare = limit - segments.size() - remaining;
                if (childSteps >= 8 && spare >= childSteps)
                    tunnel(
                            segments,
                            random.split(),
                            x,
                            y,
                            z,
                            yaw + (random.nextBoolean() ? 1 : -1) * random.nextDouble(.7, 1.3),
                            pitch * .5,
                            Math.max(config.minRadius(), width * .85),
                            childSteps,
                            childSteps,
                            false);
            }
            double horizontal = Math.cos(pitch);
            x += Math.cos(yaw) * horizontal;
            z += Math.sin(yaw) * horizontal;
            y = Math.max((double) bedrockY + 4, y + Math.sin(pitch));
            yawDrift = yawDrift * .75 + random.nextDouble(-.075, .075) * local.winding();
            pitchDrift = pitchDrift * .65 + random.nextDouble(-.025, .025);
            yaw += yawDrift;
            pitch = Math.clamp(pitch * .85 + pitchDrift, -.28, .28);
        }
    }
}
