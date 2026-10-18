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
        var variations = random.split();
        var localStart = regions.sample(x, z);
        int twistedStart = variations.nextDouble() < .4 * localStart.winding()
                ? variations.nextInt(Math.max(1, steps - 12))
                : steps;
        int twistedEnd = Math.min(steps, twistedStart + 12 + variations.nextInt(12));
        int verticalStart = branching && variations.nextDouble() < localStart.verticalChance() ? steps / 3 : steps;
        int verticalEnd = Math.min(steps, verticalStart + 24);
        boolean shaft = variations.nextInt(4) == 0;
        boolean chamber = false;
        double yawDrift = 0, pitchDrift = 0;
        for (int step = 0; step < steps && segments.size() < limit; step++) {
            var local = regions.sample(x, z);
            boolean vertical = step >= verticalStart && step < verticalEnd;
            boolean twisted = !vertical && step >= twistedStart && step < twistedEnd;
            CaveProfile profile =
                    vertical ? CaveProfile.VERTICAL : twisted ? CaveProfile.SPAGHETTI : CaveProfile.CLASSIC;
            double radius = Math.clamp(width + Math.sin(step * .17) * .18, config.minRadius(), config.maxRadius());
            if (twisted) radius = Math.clamp(radius - .2 + Math.sin(step * .55) * .12, config.minRadius(), 2);
            segments.add(new CavePlan.Segment(x, y, z, radius, Math.max(1.85, radius * .8), profile));
            if (branching
                    && !chamber
                    && !vertical
                    && step >= 8
                    && step % 18 == 8
                    && segments.size() + steps - step + 2 <= limit
                    && variations.nextDouble() < local.chamberChance() * 2
                    && chamberFits(segments, x, y, z)) {
                double room = variations.nextDouble(3.2, 6.1);
                double height = variations.nextDouble(2.2, 3);
                segments.add(new CavePlan.Segment(x, y + .3, z, room, height, CaveProfile.CHAMBER));
                segments.add(new CavePlan.Segment(
                        x + Math.cos(yaw) * .5,
                        y - .2,
                        z + Math.sin(yaw) * .5,
                        room * .85,
                        height * 1.05,
                        CaveProfile.CHAMBER));
                chamber = true;
            }
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
            if (vertical) {
                int phase = step - verticalStart;
                pitch = phase % 8 < 2 ? 0 : shaft && phase >= 12 && phase < 15 ? -1.05 : -.5;
                horizontal = Math.cos(pitch);
                yaw += .17;
            }
            x += Math.cos(yaw) * horizontal;
            z += Math.sin(yaw) * horizontal;
            y = Math.max((double) bedrockY + 4, y + Math.sin(pitch));
            double turn = twisted ? .18 : .075;
            yawDrift = yawDrift * .75 + random.nextDouble(-turn, turn) * local.winding();
            pitchDrift = pitchDrift * .65 + random.nextDouble(twisted ? -.06 : -.025, twisted ? .06 : .025);
            yaw += yawDrift;
            pitch = Math.clamp(pitch * .85 + pitchDrift, twisted ? -.45 : -.28, twisted ? .45 : .28);
        }
    }

    private static boolean chamberFits(List<CavePlan.Segment> segments, double x, double y, double z) {
        for (var segment : segments)
            if (segment.profile() == CaveProfile.CHAMBER) {
                double dx = segment.x() - x, dy = segment.y() - y, dz = segment.z() - z;
                if (dx * dx + dy * dy + dz * dz < 24 * 24) return false;
            }
        return true;
    }
}
