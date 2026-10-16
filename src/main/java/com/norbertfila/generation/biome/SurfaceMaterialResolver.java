package com.norbertfila.generation.biome;

public final class SurfaceMaterialResolver {
    private final SurfaceMaterialMapper materials;

    public SurfaceMaterialResolver(SurfaceMaterialMapper materials) {
        this.materials = materials;
    }

    public Result resolve(
            int height, double slope, int waterLevel, int baseHeight, ClimateSample climate, BiomeProfile profile) {
        long waterDepth = (long) waterLevel - height;
        double temperature = climate.temperature() - Math.max(0L, (long) height - baseHeight) * .004;
        if (waterDepth > 3)
            return new Result(materials.surface(SurfaceMaterial.GRAVEL), materials.subsurface(SurfaceMaterial.GRAVEL));
        if (waterDepth > 0 && slope > 1.5)
            return new Result(materials.surface(SurfaceMaterial.GRAVEL), materials.subsurface(SurfaceMaterial.GRAVEL));
        if (waterDepth >= -1 && slope <= 1.5) {
            SurfaceMaterial shore = temperature < -.25 ? SurfaceMaterial.GRAVEL : SurfaceMaterial.SAND;
            return new Result(materials.surface(shore), materials.subsurface(shore));
        }
        if (slope > 1.5)
            return new Result(materials.surface(SurfaceMaterial.STONE), materials.subsurface(SurfaceMaterial.STONE));
        if (temperature < -.55)
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
        SurfaceProfile surface = profile.surface();
        if (surface.surface() == SurfaceMaterial.SNOW)
            return new Result(materials.surface(SurfaceMaterial.GRASS), materials.subsurface(SurfaceMaterial.DIRT));
        return new Result(materials.surface(surface.surface()), materials.subsurface(surface.subsurface()));
    }

    public record Result(byte surface, byte subsurface) {}
}
