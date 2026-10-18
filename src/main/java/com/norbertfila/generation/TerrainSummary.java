package com.norbertfila.generation;

import com.norbertfila.generation.biome.BadlandsSurface;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;

/** Surface height describes ground; waterLevel is the highest water voxel, whose top face is one block higher. */
public record TerrainSummary(
        int surfaceHeight,
        byte surfaceMaterial,
        byte subsurfaceMaterial,
        byte deepMaterial,
        int bedrockHeight,
        int waterLevel,
        BiomeType biome,
        ClimateSample climate,
        WaterKind waterKind,
        boolean snowCover,
        boolean frozenWater,
        BadlandsSurface.Column badlands,
        TerrainShape shape) {
    public static final int NO_BEDROCK = Integer.MIN_VALUE;
    public static final int NO_WATER = Integer.MIN_VALUE;

    public TerrainSummary(
            int surfaceHeight,
            byte surfaceMaterial,
            byte subsurfaceMaterial,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel,
            BiomeType biome,
            ClimateSample climate,
            WaterKind waterKind,
            boolean snowCover,
            boolean frozenWater,
            BadlandsSurface.Column badlands) {
        this(
                surfaceHeight,
                surfaceMaterial,
                subsurfaceMaterial,
                deepMaterial,
                bedrockHeight,
                waterLevel,
                biome,
                climate,
                waterKind,
                snowCover,
                frozenWater,
                badlands,
                null);
    }

    public TerrainSummary(
            int surfaceHeight,
            byte surfaceMaterial,
            byte subsurfaceMaterial,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel,
            BiomeType biome,
            ClimateSample climate,
            WaterKind waterKind,
            boolean snowCover) {
        this(
                surfaceHeight,
                surfaceMaterial,
                subsurfaceMaterial,
                deepMaterial,
                bedrockHeight,
                waterLevel,
                biome,
                climate,
                waterKind,
                snowCover,
                false,
                null);
    }

    public TerrainSummary(
            int surfaceHeight,
            byte surfaceMaterial,
            byte subsurfaceMaterial,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel,
            BiomeType biome,
            ClimateSample climate,
            WaterKind waterKind) {
        this(
                surfaceHeight,
                surfaceMaterial,
                subsurfaceMaterial,
                deepMaterial,
                bedrockHeight,
                waterLevel,
                biome,
                climate,
                waterKind,
                false);
    }

    public TerrainSummary(
            int surfaceHeight,
            byte surfaceMaterial,
            byte subsurfaceMaterial,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel,
            BiomeType biome,
            ClimateSample climate) {
        this(
                surfaceHeight,
                surfaceMaterial,
                subsurfaceMaterial,
                deepMaterial,
                bedrockHeight,
                waterLevel,
                biome,
                climate,
                waterLevel == NO_WATER ? WaterKind.NONE : WaterKind.OCEAN);
    }

    public TerrainSummary(
            int surfaceHeight, byte surfaceMaterial, byte subsurfaceMaterial, byte deepMaterial, int bedrockHeight) {
        this(
                surfaceHeight,
                surfaceMaterial,
                subsurfaceMaterial,
                deepMaterial,
                bedrockHeight,
                NO_WATER,
                BiomeType.PLAINS,
                new ClimateSample(0, 0));
    }

    public TerrainSummary(int surfaceHeight, byte surfaceMaterial, byte subsurfaceMaterial, byte deepMaterial) {
        this(surfaceHeight, surfaceMaterial, subsurfaceMaterial, deepMaterial, NO_BEDROCK);
    }

    public TerrainSummary(
            int surfaceHeight,
            byte surfaceMaterial,
            byte subsurfaceMaterial,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel) {
        this(
                surfaceHeight,
                surfaceMaterial,
                subsurfaceMaterial,
                deepMaterial,
                bedrockHeight,
                waterLevel,
                BiomeType.PLAINS,
                new ClimateSample(0, 0));
    }

    public TerrainSummary {
        if (biome == null || climate == null || waterKind == null)
            throw new NullPointerException("Terrain data cannot be null");
        if ((waterLevel == NO_WATER) != (waterKind == WaterKind.NONE))
            throw new IllegalArgumentException("Water kind and level disagree");
        if (frozenWater && (waterLevel == NO_WATER || surfaceHeight >= waterLevel))
            throw new IllegalArgumentException("Ice requires water above the ground");
        if (snowCover && surfaceHeight < waterLevel)
            throw new IllegalArgumentException("Ground snow cannot cover a flooded column");
        if (badlands != null && biome != BiomeType.BADLANDS)
            throw new IllegalArgumentException("Badlands layers require a badlands biome");
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
        if (frozenWater && worldY == waterLevel) return BlockId.ICE;
        if (snowCover && (long) worldY == (long) surfaceHeight + 1) return BlockId.SNOW_LAYER;
        if (shape != null && worldY >= shape.minY()) return shape.materialAt(worldY);
        return materialAt(
                worldY,
                surfaceHeight,
                surfaceMaterial,
                subsurfaceMaterial,
                deepMaterial,
                bedrockHeight,
                waterLevel,
                badlands);
    }

    static byte materialAt(
            int worldY,
            int surfaceHeight,
            byte surfaceMaterial,
            byte subsurfaceMaterial,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel,
            BadlandsSurface.Column badlands) {
        if (bedrockHeight != NO_BEDROCK) {
            if (worldY < bedrockHeight) return BlockId.AIR;
            if (worldY == bedrockHeight) return BlockId.BEDROCK;
        }
        if (worldY > surfaceHeight) return worldY <= waterLevel ? BlockId.WATER : BlockId.AIR;
        if (badlands != null) return badlands.materialAt(worldY, surfaceHeight, surfaceMaterial);
        if (worldY == surfaceHeight) return surfaceMaterial;
        return worldY >= surfaceHeight - 3 ? subsurfaceMaterial : deepMaterial;
    }

    public boolean flooded() {
        return surfaceHeight < waterLevel;
    }
}
