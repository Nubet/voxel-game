package com.norbertfila.generation;

import com.norbertfila.world.BlockId;

public record TerrainSummary(int surfaceHeight, byte surfaceMaterial, byte subsurfaceMaterial, byte deepMaterial) {
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
        if (worldY > surfaceHeight) return BlockId.AIR;
        if (worldY == surfaceHeight) return surfaceMaterial;
        return worldY >= surfaceHeight - 3 ? subsurfaceMaterial : deepMaterial;
    }
}
