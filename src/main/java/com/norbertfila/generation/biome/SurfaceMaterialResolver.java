package com.norbertfila.generation.biome;

import com.norbertfila.generation.WaterKind;

public final class SurfaceMaterialResolver {
    private final SurfaceMaterialMapper materials;

    public SurfaceMaterialResolver(SurfaceMaterialMapper materials) {
        this.materials = materials;
    }

    public Result resolve(
            int height, double slope, int waterLevel, int baseHeight, ClimateSample climate, BiomeProfile profile) {
        return resolve(height, slope, waterLevel, baseHeight, climate, profile, WaterKind.OCEAN, true, 0);
    }

    public Result resolve(
            int height,
            double slope,
            int waterLevel,
            int baseHeight,
            ClimateSample climate,
            BiomeProfile profile,
            WaterKind kind,
            boolean nearWater,
            double shoreVariation) {
        long waterDepth = (long) waterLevel - height;
        double temperature = climate.temperature() - Math.max(0L, (long) height - baseHeight) * .004;
        if (waterDepth > 3)
            return new Result(materials.surface(SurfaceMaterial.GRAVEL), materials.subsurface(SurfaceMaterial.GRAVEL));
        if (waterDepth > 0 && slope > 1.5)
            return new Result(materials.surface(SurfaceMaterial.GRAVEL), materials.subsurface(SurfaceMaterial.GRAVEL));
        if (waterDepth >= -1 && slope <= 1.5 && (waterDepth > 0 || nearWater)) {
            SurfaceMaterial shore;
            if (temperature < -.25) shore = SurfaceMaterial.GRAVEL;
            else if (kind == WaterKind.RIVER || kind == WaterKind.LAKE) {
                shore = climate.humidity() > .1 && slope < .8
                        ? SurfaceMaterial.MUD
                        : shoreVariation < -.1
                                ? SurfaceMaterial.GRAVEL
                                : waterDepth > 0 ? SurfaceMaterial.DIRT : SurfaceMaterial.GRASS;
            } else {
                shore = shoreVariation < -.15
                        ? SurfaceMaterial.GRAVEL
                        : shoreVariation > .2
                                ? waterDepth > 0 ? SurfaceMaterial.DIRT : SurfaceMaterial.GRASS
                                : SurfaceMaterial.SAND;
            }
            return new Result(materials.surface(shore), materials.subsurface(shore));
        }
        if (slope > 1.5)
            return new Result(materials.surface(SurfaceMaterial.STONE), materials.subsurface(SurfaceMaterial.STONE));
        SurfaceProfile surface = profile.surface();
        if (surface.surface() == SurfaceMaterial.PODZOL)
            return new Result(materials.surface(surface.surface()), materials.subsurface(surface.subsurface()));
        if (temperature < -.8 && height > (long) baseHeight + 150)
            return new Result(materials.surface(SurfaceMaterial.ICE), materials.subsurface(SurfaceMaterial.ICE));
        if (surface.surface() == SurfaceMaterial.SNOW || temperature < -.55)
            return new Result(materials.surface(SurfaceMaterial.SNOW), materials.subsurface(SurfaceMaterial.SNOW));
        if (temperature < -.3)
            return new Result(materials.surface(SurfaceMaterial.STONE), materials.subsurface(SurfaceMaterial.STONE));
        if (profile.surface().surface() == SurfaceMaterial.GRAVEL && slope <= 1.5) {
            SurfaceMaterial band = switch (Math.floorMod(height - baseHeight, 24) / 8) {
                case 0 -> SurfaceMaterial.TERRACOTTA_RED;
                case 1 -> SurfaceMaterial.TERRACOTTA_ORANGE;
                default -> SurfaceMaterial.CLAY;
            };
            return new Result(materials.surface(band), materials.subsurface(band));
        }
        if (surface.surface() == SurfaceMaterial.GRASS && climate.temperature() > .1 && climate.humidity() < -.15)
            return new Result(
                    materials.surface(SurfaceMaterial.DRY_GRASS), materials.subsurface(SurfaceMaterial.DRY_GRASS));
        return new Result(materials.surface(surface.surface()), materials.subsurface(surface.subsurface()));
    }

    public record Result(byte surface, byte subsurface) {}
}
