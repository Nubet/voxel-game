package com.norbertfila.generation;

import com.norbertfila.world.BlockId;

public record TerrainSummary(
        int surfaceHeight, byte surfaceMaterial, byte subsurfaceMaterial, byte deepMaterial, int bedrockHeight) {
    public static final int NO_BEDROCK = Integer.MIN_VALUE;

    public TerrainSummary(int surfaceHeight, byte surfaceMaterial, byte subsurfaceMaterial, byte deepMaterial) {
        this(surfaceHeight, surfaceMaterial, subsurfaceMaterial, deepMaterial, NO_BEDROCK);
    }

    public TerrainSummary {
        if (!BlockId.isValid(surfaceMaterial)
                || !BlockId.isValid(subsurfaceMaterial)
                || !BlockId.isValid(deepMaterial)
                || surfaceMaterial == BlockId.AIR
                || subsurfaceMaterial == BlockId.AIR
                || deepMaterial == BlockId.AIR) {
            throw new IllegalArgumentException("Terrain materials must be valid solid blocks");
        }
    }

    public byte materialAt(int worldY) {
        if (bedrockHeight != NO_BEDROCK) {
            if (worldY < bedrockHeight) return BlockId.AIR;
            if (worldY == bedrockHeight) return BlockId.BEDROCK;
        }
        if (worldY > surfaceHeight) return BlockId.AIR;
        if (worldY == surfaceHeight) return surfaceMaterial;
        return worldY >= surfaceHeight - 3 ? subsurfaceMaterial : deepMaterial;
    }
}
