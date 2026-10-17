package com.norbertfila.generation.biome;

import com.norbertfila.world.BlockId;

public final class SurfaceMaterialMapper {
    public byte surface(SurfaceMaterial material) {
        return switch (material) {
            case GRASS -> BlockId.GRASS;
            case DRY_GRASS -> BlockId.DRY_GRASS;
            case DIRT -> BlockId.DIRT;
            case SAND -> BlockId.SAND;
            case GRAVEL -> BlockId.GRAVEL;
            case STONE -> BlockId.STONE;
            case SNOW -> BlockId.SNOW;
            case MUD -> BlockId.MUD;
            case PODZOL -> BlockId.PODZOL;
            case COARSE_DIRT -> BlockId.COARSE_DIRT;
            case ICE -> BlockId.ICE;
            case CLAY -> BlockId.CLAY;
            case TERRACOTTA_RED -> BlockId.TERRACOTTA_RED;
            case TERRACOTTA_ORANGE -> BlockId.TERRACOTTA_ORANGE;
        };
    }

    public byte subsurface(SurfaceMaterial material) {
        return switch (material) {
            case GRASS, DRY_GRASS, DIRT -> BlockId.DIRT;
            case SAND -> BlockId.SAND;
            case GRAVEL -> BlockId.GRAVEL;
            case STONE, SNOW -> BlockId.STONE;
            case MUD -> BlockId.MUD;
            case PODZOL, COARSE_DIRT -> BlockId.COARSE_DIRT;
            case ICE -> BlockId.PACKED_ICE;
            case CLAY -> BlockId.CLAY;
            case TERRACOTTA_RED -> BlockId.TERRACOTTA_RED;
            case TERRACOTTA_ORANGE -> BlockId.TERRACOTTA_ORANGE;
        };
    }
}
