package com.norbertfila.generation;

import com.norbertfila.world.WorldBounds;
import java.util.Objects;

final class RiftTerrainFeature {
    private final RiftPlanner planner;
    private final GroundSampler ground;
    private final GenerationCache<Boolean> eligibility = new GenerationCache<>(256);

    RiftTerrainFeature(long seed, GroundSampler ground) {
        planner = new RiftPlanner(seed);
        this.ground = Objects.requireNonNull(ground);
    }

    boolean possible(int x, int z) {
        return planner.at(x, z) != null;
    }

    TerrainSummary apply(int x, int z, TerrainSummary base) {
        var plan = planAt(x, z);
        return plan == null ? base : carve(plan, x, z, base);
    }

    RiftPlanner.Plan planAt(int x, int z) {
        var plan = planner.at(x, z);
        if (plan == null) return null;
        boolean allowed = eligibility.get(
                Math.floorDiv(x, RiftPlanner.REGION_SIZE),
                Math.floorDiv(z, RiftPlanner.REGION_SIZE),
                () -> suitable(plan));
        return allowed ? plan : null;
    }

    TerrainSource.SurfaceSample sampleArea(int ox, int oz, int size, TerrainSource.SurfaceSample center) {
        int endX = ox + size - 1, endZ = oz + size - 1;
        var selected = center;
        for (int rz = Math.floorDiv(oz, RiftPlanner.REGION_SIZE);
                rz <= Math.floorDiv(endZ, RiftPlanner.REGION_SIZE);
                rz++)
            for (int rx = Math.floorDiv(ox, RiftPlanner.REGION_SIZE);
                    rx <= Math.floorDiv(endX, RiftPlanner.REGION_SIZE);
                    rx++) {
                var candidate = planner.plan(rx, rz);
                if (candidate.isEmpty()) continue;
                var p = candidate.get();
                if (endX < p.minX() - size
                        || ox > p.maxX() + size
                        || endZ < p.minZ() - size
                        || oz > p.maxZ() + size
                        || planAt(p.minX(), p.minZ()) == null) continue;
                selected = new TerrainSource.SurfaceSample(selected.x(), selected.z(), selected.terrain(), true);
                if (size == 1) continue;
                for (int z = Math.max(oz, p.minZ()); z <= Math.min(endZ, p.maxZ()); z++)
                    for (int x = Math.max(ox, p.minX()); x <= Math.min(endX, p.maxX()); x++) {
                        if (p.section(x + .5, z + .5) == null) continue;
                        var base = ground.sample(x, z);
                        var cut = carve(p, x, z, base);
                        if (cut.surfaceHeight() < base.surfaceHeight()
                                && cut.surfaceHeight() < selected.terrain().surfaceHeight())
                            selected = new TerrainSource.SurfaceSample(x, z, cut, true);
                    }
            }
        return selected;
    }

    private boolean suitable(RiftPlanner.Plan plan) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        // Check every column plus a dry bank so even one-block streams cannot leak into the cut.
        for (int z = plan.minZ() - 6; z <= plan.maxZ() + 6; z++)
            for (int x = plan.minX() - 6; x <= plan.maxX() + 6; x++) {
                if (plan.section(x + .5, z + .5, 6) == null) continue;
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                var base = ground.sample(x, z);
                if (base.shape() != null
                        || base.badlands() != null
                        || base.biome().rareClassic()
                        || base.surfaceHeight() <= (long) base.waterLevel() + 3
                        || base.surfaceHeight() < (long) base.bedrockHeight() + 46) return false;
                switch (base.biome()) {
                    case SWAMP, SWAMPLAND, OCEAN, LAKE, RIVER, ALPINE, STONY_SLOPES -> {
                        return false;
                    }
                    default -> {}
                }
                low = Math.min(low, base.surfaceHeight());
                high = Math.max(high, base.surfaceHeight());
                if (high - (long) low > 18
                        || Math.abs((long) base.surfaceHeight() - ground.surfaceHeight(x + 1, z)) > 3
                        || Math.abs((long) base.surfaceHeight() - ground.surfaceHeight(x, z + 1)) > 3) return false;
            }
        return true;
    }

    static TerrainSummary carve(RiftPlanner.Plan plan, int x, int z, TerrainSummary base) {
        if (base.flooded() || base.shape() != null) return base;
        var section = plan.section(x + .5, z + .5);
        if (section == null) return base;
        double across = Math.abs(section.lateral()) / section.halfWidth();
        double exponent = section.lateral() < 0 ? 5 : 8;
        double drop = section.depth() * (1 - Math.pow(across, exponent));
        if (section.lateral() < 0 && across > .5 && across < .83) {
            double ledge = RiftPlanner.smooth((section.along() - .28) / .08)
                    * RiftPlanner.smooth((.65 - section.along()) / .08);
            drop -= ledge * Math.max(0, drop - section.depth() * .55);
        }
        int cut = (int) Math.floor(drop);
        int floor = (int) Math.max(WorldBounds.MIN_Y + 4L, (long) base.bedrockHeight() + 4);
        int height = Math.max(floor, base.surfaceHeight() - cut);
        if (height >= base.surfaceHeight()) return base;
        return new TerrainSummary(
                height,
                base.materialAt(height),
                base.materialAt(height - 1),
                base.deepMaterial(),
                base.bedrockHeight(),
                TerrainSummary.NO_WATER,
                base.biome(),
                base.climate(),
                WaterKind.NONE,
                false,
                false,
                base.badlands());
    }
}
