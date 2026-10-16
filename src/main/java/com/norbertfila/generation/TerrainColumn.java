package com.norbertfila.generation;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import java.util.BitSet;
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
    private final BiomeType[] biomes;
    private final byte deepMaterial;
    private final int bedrockHeight;
    private final int waterLevel;
    private final List<TreeGenerator.Tree> trees;
    private final List<CavePlan.Segment> caves;
    private final BitSet removedSurface;
    private final int minSurface;
    private final int highestFeature;

    private TerrainColumn(
            int[] surfaceHeights,
            byte[] surfaceMaterials,
            byte[] subsurfaceMaterials,
            BiomeType[] biomes,
            byte deepMaterial,
            int bedrockHeight,
            int waterLevel,
            List<TreeGenerator.Tree> trees,
            List<CavePlan.Segment> caves,
            BitSet removedSurface) {
        this.surfaceHeights = surfaceHeights;
        this.surfaceMaterials = surfaceMaterials;
        this.subsurfaceMaterials = subsurfaceMaterials;
        this.biomes = biomes;
        this.deepMaterial = deepMaterial;
        this.bedrockHeight = bedrockHeight;
        this.waterLevel = waterLevel;
        this.trees = List.copyOf(trees);
        this.caves = List.copyOf(caves);
        this.removedSurface = (BitSet) removedSurface.clone();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int height : surfaceHeights) {
            min = Math.min(min, height);
            max = Math.max(max, height);
        }
        // Plants sit one block above the surface; tree crowns end two blocks above the trunk.
        long highest = (long) max + 1;
        for (TreeGenerator.Tree tree : this.trees)
            highest = Math.max(highest, (long) tree.baseY() + tree.trunkHeight() + 2);
        minSurface = min;
        highestFeature = (int) Math.min(Integer.MAX_VALUE, highest);
    }

    /**
     * The single block filling world heights {@code [minY, minY + 15]} of this column, or -1 when the column bounds
     * cannot prove the section uniform. Uniform sections skip block filling and decoration entirely.
     */
    int uniformBlock(int minY) {
        long maxY = (long) minY + Chunk.SIZE - 1;
        if (bedrockHeight != TerrainSummary.NO_BEDROCK && maxY < bedrockHeight) return Byte.toUnsignedInt(BlockId.AIR);
        if ((bedrockHeight == TerrainSummary.NO_BEDROCK || minY > bedrockHeight) && maxY < (long) minSurface - 3)
            return Byte.toUnsignedInt(deepMaterial);
        if (minY > highestFeature) {
            if (minY > waterLevel) return Byte.toUnsignedInt(BlockId.AIR);
            if (maxY <= waterLevel) return Byte.toUnsignedInt(BlockId.WATER);
        }
        return -1;
    }

    /**
     * Builds a column from per-column summaries indexed {@code z * 16 + x}. Deep material, bedrock and water level
     * must be shared by all summaries, which holds for every summary a {@link TerrainGenerator} produces.
     */
    static TerrainColumn of(TerrainSummary[] summaries, List<TreeGenerator.Tree> trees) {
        return of(summaries, trees, List.of(), new BitSet());
    }

    static TerrainColumn of(
            TerrainSummary[] summaries,
            List<TreeGenerator.Tree> trees,
            List<CavePlan.Segment> caves,
            BitSet removedSurface) {
        if (summaries.length != AREA) throw new IllegalArgumentException("Column needs " + AREA + " summaries");
        TerrainSummary first = Objects.requireNonNull(summaries[0]);
        int[] heights = new int[AREA];
        byte[] surface = new byte[AREA], subsurface = new byte[AREA];
        BiomeType[] biomes = new BiomeType[AREA];
        for (int index = 0; index < AREA; index++) {
            TerrainSummary summary = summaries[index];
            if (summary.deepMaterial() != first.deepMaterial()
                    || summary.bedrockHeight() != first.bedrockHeight()
                    || summary.waterLevel() != first.waterLevel())
                throw new IllegalArgumentException("Column summaries must share deep material, bedrock and water");
            heights[index] = summary.surfaceHeight();
            surface[index] = summary.surfaceMaterial();
            subsurface[index] = summary.subsurfaceMaterial();
            biomes[index] = summary.biome();
        }
        return new TerrainColumn(
                heights,
                surface,
                subsurface,
                biomes,
                first.deepMaterial(),
                first.bedrockHeight(),
                first.waterLevel(),
                trees,
                caves,
                removedSurface);
    }

    int surfaceHeight(int index) {
        return surfaceHeights[index];
    }

    byte surfaceMaterial(int index) {
        return surfaceMaterials[index];
    }

    BiomeType biome(int index) {
        return biomes[index];
    }

    int waterLevel() {
        return waterLevel;
    }

    List<TreeGenerator.Tree> trees() {
        return trees;
    }

    List<CavePlan.Segment> caves() {
        return caves;
    }

    boolean surfaceSupported(int index) {
        return !removedSurface.get(index);
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
        return AREA * (Integer.BYTES + 2L) + trees.size() * 48L + caves.size() * 8L + removedSurface.size() / 8L;
    }
}
