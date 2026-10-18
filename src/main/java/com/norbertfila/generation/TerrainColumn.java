package com.norbertfila.generation;

import com.norbertfila.generation.biome.BadlandsSurface;
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
    private final byte[] deepMaterials;
    private final BadlandsSurface.Column[] badlands;
    private final TerrainShape[] shapes;
    private final int minShape, maxShape;
    private final int bedrockHeight;
    private final int[] waterLevels;
    private final int minWater;
    private final int maxWater;
    private final List<TreeGenerator.Tree> trees;
    private final List<CavePlan.Segment> caves;
    private final List<CaveFeatures.Feature> caveFeatures;
    private final BitSet removedSurface;
    private final BitSet snowCover;
    private final BitSet frozenWater;
    private final int minSurface;
    private final int highestFeature;

    private TerrainColumn(
            int[] surfaceHeights,
            byte[] surfaceMaterials,
            byte[] subsurfaceMaterials,
            BiomeType[] biomes,
            byte[] deepMaterials,
            BadlandsSurface.Column[] badlands,
            TerrainShape[] shapes,
            int bedrockHeight,
            int[] waterLevels,
            List<TreeGenerator.Tree> trees,
            List<CavePlan.Segment> caves,
            List<CaveFeatures.Feature> caveFeatures,
            BitSet removedSurface,
            BitSet snowCover,
            BitSet frozenWater) {
        this.surfaceHeights = surfaceHeights;
        this.surfaceMaterials = surfaceMaterials;
        this.subsurfaceMaterials = subsurfaceMaterials;
        this.biomes = biomes;
        this.deepMaterials = deepMaterials;
        this.badlands = badlands;
        this.shapes = shapes;
        int shapeMin = Integer.MAX_VALUE, shapeMax = Integer.MIN_VALUE;
        for (var shape : shapes)
            if (shape != null) {
                shapeMin = Math.min(shapeMin, shape.minY());
                shapeMax = Math.max(shapeMax, shape.maxY());
            }
        minShape = shapeMin;
        maxShape = shapeMax;
        this.bedrockHeight = bedrockHeight;
        this.waterLevels = waterLevels;
        minWater = java.util.Arrays.stream(waterLevels).min().orElseThrow();
        maxWater = java.util.Arrays.stream(waterLevels).max().orElseThrow();
        this.trees = List.copyOf(trees);
        this.caves = List.copyOf(caves);
        this.caveFeatures = List.copyOf(caveFeatures);
        this.removedSurface = (BitSet) removedSurface.clone();
        this.snowCover = (BitSet) snowCover.clone();
        this.frozenWater = (BitSet) frozenWater.clone();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int height : surfaceHeights) {
            min = Math.min(min, height);
            max = Math.max(max, height);
        }
        long highest = (long) max + 3;
        for (TreeGenerator.Tree tree : this.trees) highest = Math.max(highest, tree.maxY());
        minSurface = min;
        highestFeature = (int) Math.min(Integer.MAX_VALUE, highest);
    }

    /**
     * The single block filling world heights {@code [minY, minY + 15]} of this column, or -1 when the column bounds
     * cannot prove the section uniform. Uniform sections skip block filling and decoration entirely.
     */
    int uniformBlock(int minY) {
        long maxY = (long) minY + Chunk.SIZE - 1;
        if (minY <= maxShape && maxY >= minShape) return -1;
        if (!frozenWater.isEmpty() && minY <= maxWater && maxY >= minWater) return -1;
        if (bedrockHeight != TerrainSummary.NO_BEDROCK && maxY < bedrockHeight) return Byte.toUnsignedInt(BlockId.AIR);
        if ((bedrockHeight == TerrainSummary.NO_BEDROCK || minY > bedrockHeight) && maxY < (long) minSurface - 3) {
            byte material = materialAt(0, minY);
            boolean uniform = true;
            for (int index = 0; index < AREA && uniform; index++)
                uniform = (badlands[index] == null
                                || maxY < (long) surfaceHeights[index] - BadlandsSurface.TERRACOTTA_DEPTH)
                        && materialAt(index, minY) == material;
            if (uniform) return Byte.toUnsignedInt(material);
        }
        if (minY > highestFeature) {
            if (minY > maxWater) return Byte.toUnsignedInt(BlockId.AIR);
            if (maxY <= minWater) return Byte.toUnsignedInt(BlockId.WATER);
        }
        return -1;
    }

    /**
     * Builds a column from per-column summaries indexed {@code z * 16 + x}. Bedrock must be shared.
     */
    static TerrainColumn of(TerrainSummary[] summaries, List<TreeGenerator.Tree> trees) {
        return of(summaries, trees, List.of(), new BitSet());
    }

    static TerrainColumn of(
            TerrainSummary[] summaries,
            List<TreeGenerator.Tree> trees,
            List<CavePlan.Segment> caves,
            BitSet removedSurface) {
        return of(summaries, trees, caves, removedSurface, List.of());
    }

    static TerrainColumn of(
            TerrainSummary[] summaries,
            List<TreeGenerator.Tree> trees,
            List<CavePlan.Segment> caves,
            BitSet removedSurface,
            List<CaveFeatures.Feature> caveFeatures) {
        if (summaries.length != AREA) throw new IllegalArgumentException("Column needs " + AREA + " summaries");
        TerrainSummary first = Objects.requireNonNull(summaries[0]);
        int[] heights = new int[AREA];
        int[] waterLevels = new int[AREA];
        byte[] surface = new byte[AREA], subsurface = new byte[AREA];
        byte[] deep = new byte[AREA];
        var badlands = new BadlandsSurface.Column[AREA];
        var shapes = new TerrainShape[AREA];
        BiomeType[] biomes = new BiomeType[AREA];
        BitSet snow = new BitSet(AREA);
        BitSet ice = new BitSet(AREA);
        for (int index = 0; index < AREA; index++) {
            TerrainSummary summary = summaries[index];
            if (summary.bedrockHeight() != first.bedrockHeight())
                throw new IllegalArgumentException("Column summaries must share bedrock");
            heights[index] = summary.surfaceHeight();
            surface[index] = summary.surfaceMaterial();
            subsurface[index] = summary.subsurfaceMaterial();
            biomes[index] = summary.biome();
            waterLevels[index] = summary.waterLevel();
            deep[index] = summary.deepMaterial();
            badlands[index] = summary.badlands();
            shapes[index] = summary.shape();
            if (summary.snowCover()) snow.set(index);
            if (summary.frozenWater()) ice.set(index);
        }
        return new TerrainColumn(
                heights,
                surface,
                subsurface,
                biomes,
                deep,
                badlands,
                shapes,
                first.bedrockHeight(),
                waterLevels,
                trees,
                caves,
                caveFeatures,
                removedSurface,
                snow,
                ice);
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

    int waterLevel(int index) {
        return waterLevels[index];
    }

    List<TreeGenerator.Tree> trees() {
        return trees;
    }

    List<CavePlan.Segment> caves() {
        return caves;
    }

    List<CaveFeatures.Feature> caveFeatures() {
        return caveFeatures;
    }

    boolean surfaceSupported(int index) {
        return !removedSurface.get(index);
    }

    boolean snowCover(int index) {
        return snowCover.get(index);
    }

    byte materialAt(int index, int worldY) {
        if (frozenWater.get(index) && worldY == waterLevels[index]) return BlockId.ICE;
        if (shapes[index] != null && worldY >= shapes[index].minY()) return shapes[index].materialAt(worldY);
        return TerrainSummary.materialAt(
                worldY,
                surfaceHeights[index],
                surfaceMaterials[index],
                subsurfaceMaterials[index],
                deepMaterials[index],
                bedrockHeight,
                waterLevels[index],
                badlands[index]);
    }

    long estimatedBytes() {
        long layerBytes = AREA * 2L * Long.BYTES;
        for (var column : badlands) if (column != null) layerBytes += 24;
        for (var shape : shapes) if (shape != null) layerBytes += 32L + shape.maxY() - shape.minY() + 1;
        return layerBytes
                + AREA * (2L * Integer.BYTES + 3L)
                + trees.size() * 48L
                + caves.size() * 8L
                + caveFeatures.size() * 8L
                + (removedSurface.size() + snowCover.size() + frozenWater.size()) / 8L;
    }

    BitSet fallingWater(int originY, byte[] blocks) {
        var result = new BitSet();
        for (int column = 0; column < AREA; column++)
            if (shapes[column] != null)
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int index = y * AREA + column;
                    if (blocks[index] == BlockId.WATER
                            && shapes[column].waterState(originY + y) == com.norbertfila.world.WaterState.FALLING)
                        result.set(index);
                }
        return result;
    }
}
