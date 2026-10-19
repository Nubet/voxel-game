package com.norbertfila.generation;

import com.norbertfila.world.WorldBounds;

final class RiftTerrainFeature {
    private final RiftPlanner planner;

    RiftTerrainFeature(long seed) {
        planner = new RiftPlanner(seed);
    }

    boolean possible(int x, int z) {
        return planner.at(x, z) != null;
    }

    TerrainSummary apply(int x, int z, TerrainSummary base) {
        var plan = planner.at(x, z);
        return plan == null ? base : carve(plan, x, z, base);
    }

    static TerrainSummary carve(RiftPlanner.Plan plan, int x, int z, TerrainSummary base) {
        if (base.flooded() || base.shape() != null) return base;
        var section = plan.section(x + .5, z + .5);
        if (section == null) return base;
        double across = Math.abs(section.lateral()) / section.halfWidth();
        double exponent = section.lateral() < 0 ? 5 : 8;
        int cut = (int) Math.floor(section.depth() * (1 - Math.pow(across, exponent)));
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
