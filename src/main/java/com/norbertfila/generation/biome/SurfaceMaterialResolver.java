package com.norbertfila.generation.biome;

public final class SurfaceMaterialResolver {
    private final SurfaceMaterialMapper materials;

    public SurfaceMaterialResolver(SurfaceMaterialMapper materials) {
        this.materials = materials;
    }

    public Result resolve(
            int height, double slope, int waterLevel, int baseHeight, ClimateSample climate, BiomeProfile profile) {
        long waterDepth = (long) waterLevel - height;
        if (waterDepth > 3)
            return new Result(materials.surface(SurfaceMaterial.GRAVEL), materials.subsurface(SurfaceMaterial.GRAVEL));
        if (waterDepth >= -1 && slope <= 1.5)
            return new Result(materials.surface(SurfaceMaterial.SAND), materials.subsurface(SurfaceMaterial.SAND));
        if (slope > 1.5)
            return new Result(materials.surface(SurfaceMaterial.STONE), materials.subsurface(SurfaceMaterial.STONE));
        if (profile.surface().surface() == SurfaceMaterial.SNOW
                || height >= (long) baseHeight + 70 && climate.temperature() < -.35f)
            return new Result(materials.surface(SurfaceMaterial.SNOW), materials.subsurface(SurfaceMaterial.SNOW));
        SurfaceProfile surface = profile.surface();
        return new Result(materials.surface(surface.surface()), materials.subsurface(surface.subsurface()));
    }

    public record Result(byte surface, byte subsurface) {}
}
