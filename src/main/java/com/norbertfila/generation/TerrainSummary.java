package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;

/** Surface height describes ground; waterLevel is the highest water voxel, whose top face is one block higher. */
public record TerrainSummary(
        int surfaceHeight,
        byte surfaceMaterial,
        byte subsurfaceMaterial,
        byte deepMaterial,
        int bedrockHeight,
        int waterLevel) {
    public static final int NO_BEDROCK = Integer.MIN_VALUE;
    public static final int NO_WATER = Integer.MIN_VALUE;

    public TerrainSummary(
            int surfaceHeight, byte surfaceMaterial, byte subsurfaceMaterial, byte deepMaterial, int bedrockHeight) {
        this(surfaceHeight, surfaceMaterial, subsurfaceMaterial, deepMaterial, bedrockHeight, NO_WATER);
    }

    public TerrainSummary(int surfaceHeight, byte surfaceMaterial, byte subsurfaceMaterial, byte deepMaterial) {
        this(surfaceHeight, surfaceMaterial, subsurfaceMaterial, deepMaterial, NO_BEDROCK);
    }

    public TerrainSummary {
        if (!BlockId.isValid(surfaceMaterial)
                || !BlockId.isValid(subsurfaceMaterial)
                || !BlockId.isValid(deepMaterial)
                || !BlockRegistry.DEFAULT.isCollidable(surfaceMaterial)
                || !BlockRegistry.DEFAULT.isCollidable(subsurfaceMaterial)
                || !BlockRegistry.DEFAULT.isCollidable(deepMaterial)) {
            throw new IllegalArgumentException("Terrain materials must be valid solid blocks");
        }
    }

    public byte materialAt(int worldY) {
        if (bedrockHeight != NO_BEDROCK) {
            if (worldY < bedrockHeight) return BlockId.AIR;
            if (worldY == bedrockHeight) return BlockId.BEDROCK;
        }
        if (worldY > surfaceHeight) return worldY <= waterLevel ? BlockId.WATER : BlockId.AIR;
        if (worldY == surfaceHeight) return surfaceMaterial;
        return worldY >= surfaceHeight - 3 ? subsurfaceMaterial : deepMaterial;
    }

    public boolean flooded() {
        return surfaceHeight < waterLevel;
    }
}
