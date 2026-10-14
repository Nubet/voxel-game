package com.norbertfila.generation;

import com.norbertfila.world.Chunk;
import java.util.List;
import java.util.Objects;

/**
 * Immutable terrain of one 16x16 chunk column: per-column surface data and the trees that reach into it. Every
 * vertical section of the column is built from the same instance, so noise sampling and tree placement decisions
 * happen once per column instead of once per section.
 */
final class TerrainColumn {
    static final int AREA = Chunk.SIZE * Chunk.SIZE;

    private final int[] surfaceHeights;
    private final byte[] surfaceMaterials;
    private final byte[] subsurfaceMaterials;
    private final byte deepMaterial;
    private final int bedrockHeight;
    private final int waterLevel;
    private final List<TreeGenerator.Tree> trees;

    private TerrainColumn(
            int[] surfaceHeights,
            byte[] surfaceMaterials,
            byte[] subsurfaceMaterials,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel,
            List<TreeGenerator.Tree> trees) {
        this.surfaceHeights = surfaceHeights;
        this.surfaceMaterials = surfaceMaterials;
        this.subsurfaceMaterials = subsurfaceMaterials;
        this.deepMaterial = deepMaterial;
        this.bedrockHeight = bedrockHeight;
        this.waterLevel = waterLevel;
        this.trees = List.copyOf(trees);
    }

    /**
     * Builds a column from per-column summaries indexed {@code z * 16 + x}. Deep material, bedrock and water level
     * must be shared by all summaries, which holds for every summary a {@link TerrainGenerator} produces.
     */
    static TerrainColumn of(TerrainSummary[] summaries, List<TreeGenerator.Tree> trees) {
        if (summaries.length != AREA) throw new IllegalArgumentException("Column needs " + AREA + " summaries");
        TerrainSummary first = Objects.requireNonNull(summaries[0]);
        int[] heights = new int[AREA];
        byte[] surface = new byte[AREA], subsurface = new byte[AREA];
        for (int index = 0; index < AREA; index++) {
            TerrainSummary summary = summaries[index];
            if (summary.deepMaterial() != first.deepMaterial()
                    || summary.bedrockHeight() != first.bedrockHeight()
                    || summary.waterLevel() != first.waterLevel())
                throw new IllegalArgumentException("Column summaries must share deep material, bedrock and water");
            heights[index] = summary.surfaceHeight();
            surface[index] = summary.surfaceMaterial();
            subsurface[index] = summary.subsurfaceMaterial();
        }
        return new TerrainColumn(
                heights, surface, subsurface, first.deepMaterial(), first.bedrockHeight(), first.waterLevel(), trees);
    }

    int surfaceHeight(int index) {
        return surfaceHeights[index];
    }

    byte surfaceMaterial(int index) {
        return surfaceMaterials[index];
    }

    int waterLevel() {
        return waterLevel;
    }

    List<TreeGenerator.Tree> trees() {
        return trees;
    }

    byte materialAt(int index, int worldY) {
        return TerrainSummary.materialAt(
                worldY,
                surfaceHeights[index],
                surfaceMaterials[index],
                subsurfaceMaterials[index],
                deepMaterial,
                bedrockHeight,
                waterLevel);
    }

    long estimatedBytes() {
        return AREA * (Integer.BYTES + 2L) + trees.size() * 48L;
    }
}
