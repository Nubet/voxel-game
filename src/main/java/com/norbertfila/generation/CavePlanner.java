package com.norbertfila.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.IntBinaryOperator;

final class CavePlanner {
    static final int REGION_SIZE = 128;
    static final int MAX_REACH = 96;
    static final int MAX_SEGMENTS = 512;
    private static final int MAX_STEPS = MAX_REACH - 8;
    private static final long SALT = 0xA24BAED4963EE407L;
    private final long seed;
    private final int bedrockY;
    private final IntBinaryOperator surfaceHeight;

    CavePlanner(long seed, int bedrockY, IntBinaryOperator surfaceHeight) {
        this.seed = seed;
        this.bedrockY = bedrockY;
        this.surfaceHeight = Objects.requireNonNull(surfaceHeight);
    }

    CavePlan plan(int regionX, int regionZ) {
        long ox = (long) regionX * REGION_SIZE, oz = (long) regionZ * REGION_SIZE;
        if (ox < Integer.MIN_VALUE
                || ox + REGION_SIZE - 1 > Integer.MAX_VALUE
                || oz < Integer.MIN_VALUE
                || oz + REGION_SIZE - 1 > Integer.MAX_VALUE) return new CavePlan(List.of());
        var random = new SplittableRandom(GenerationHash.hash(seed ^ SALT, regionX, regionZ));
        List<CavePlan.Segment> segments = new ArrayList<>();
        int systems = 4 + random.nextInt(5);
        for (int system = 0; system < systems && segments.size() < MAX_SEGMENTS; system++) {
            int x = (int) ox + random.nextInt(REGION_SIZE), z = (int) oz + random.nextInt(REGION_SIZE);
            long bottom = (long) bedrockY + 5, top = (long) surfaceHeight.applyAsInt(x, z) - 1;
            if (top < bottom) continue;
            double y = bottom + random.nextLong(top - bottom + 1) + .5;
            if (random.nextInt(4) == 0) {
                double radius = random.nextDouble(4, 7);
                segments.add(new CavePlan.Segment(x + .5, y, z + .5, radius, radius * .65));
            }
            tunnel(
                    segments,
                    random.split(),
                    x + .5,
                    y,
                    z + .5,
                    random.nextDouble(Math.PI * 2),
                    random.nextDouble(-.2, .2),
                    random.nextDouble(1.5, 3.2),
                    48 + random.nextInt(MAX_STEPS - 47),
                    0);
        }
        return new CavePlan(segments);
    }

    private static void tunnel(
            List<CavePlan.Segment> segments,
            SplittableRandom random,
            double x,
            double y,
            double z,
            double yaw,
            double pitch,
            double width,
            int steps,
            int depth) {
        double yawDrift = 0, pitchDrift = 0;
        int branch = steps / 3 + random.nextInt(Math.max(1, steps / 3));
        for (int step = 0; step < steps && segments.size() < MAX_SEGMENTS; step++) {
            double radius = 1 + Math.sin(Math.PI * step / steps) * width;
            segments.add(new CavePlan.Segment(x, y, z, radius, radius * .7));
            if (depth == 0 && step == branch && random.nextInt(3) != 0) {
                // Children inherit the remaining path budget, keeping every branch within MAX_REACH of its source.
                tunnel(
                        segments,
                        random.split(),
                        x,
                        y,
                        z,
                        yaw - Math.PI / 2,
                        pitch / 3,
                        width * .75,
                        steps - step,
                        depth + 1);
                tunnel(
                        segments,
                        random.split(),
                        x,
                        y,
                        z,
                        yaw + Math.PI / 2,
                        pitch / 3,
                        width * .75,
                        steps - step,
                        depth + 1);
                return;
            }
            double horizontal = Math.cos(pitch);
            x += Math.cos(yaw) * horizontal;
            y += Math.sin(pitch);
            z += Math.sin(yaw) * horizontal;
            yawDrift = yawDrift * .7 + random.nextDouble(-.12, .12);
            pitchDrift = pitchDrift * .7 + random.nextDouble(-.04, .04);
            yaw += yawDrift;
            pitch = Math.clamp(pitch * .9 + pitchDrift, -.6, .6);
        }
    }
}
