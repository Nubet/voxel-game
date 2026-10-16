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
            case MUD -> BlockId.MUD;
            case PODZOL -> BlockId.GRASS;
            case COARSE_DIRT -> BlockId.DIRT;
            case ICE -> BlockId.SNOW;
            case CLAY -> BlockId.STONE;
            case TERRACOTTA_RED -> BlockId.GRAVEL;
            case TERRACOTTA_ORANGE -> BlockId.SANDSTONE;
        };
    }

    public byte subsurface(SurfaceMaterial material) {
        return switch (material) {
            case GRASS, DIRT -> BlockId.DIRT;
            case SAND -> BlockId.SAND;
            case GRAVEL -> BlockId.GRAVEL;
            case STONE, SNOW -> BlockId.STONE;
            case MUD -> BlockId.MUD;
            case PODZOL, COARSE_DIRT -> BlockId.DIRT;
            case ICE -> BlockId.STONE;
            case CLAY -> BlockId.STONE;
            case TERRACOTTA_RED -> BlockId.GRAVEL;
            case TERRACOTTA_ORANGE -> BlockId.SANDSTONE;
        };
    }
}
