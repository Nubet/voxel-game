package com.norbertfila.generation.biome;

import com.norbertfila.world.BlockId;

public final class SurfaceMaterialMapper {
    public byte surface(SurfaceMaterial material) {
        return switch (material) {
            case GRASS -> BlockId.GRASS;
            case DIRT -> BlockId.DIRT;
            case SAND -> BlockId.SAND;
            case GRAVEL -> BlockId.GRAVEL;
            case STONE -> BlockId.STONE;
            case SNOW -> BlockId.SNOW;
        };
    }

    public byte subsurface(SurfaceMaterial material) {
        return switch (material) {
            case GRASS, DIRT -> BlockId.DIRT;
            case SAND -> BlockId.SANDSTONE;
            case GRAVEL -> BlockId.GRAVEL;
            case STONE, SNOW -> BlockId.STONE;
        };
    }
}
