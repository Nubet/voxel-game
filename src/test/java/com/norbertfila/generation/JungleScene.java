package com.norbertfila.generation;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.*;

final class JungleScene implements TerrainSource {
    private final TreeGenerator trees = new TreeGenerator(
            42,
            (x, z) -> 1,
            this::sample,
            BiomeRegistry.defaults(),
            new LandscapeSampler(42, LandscapeTestConfig.uniform()));
    private final boolean slope;
    private final boolean tropical;
    private final JungleVegetation jungle = new JungleVegetation(42);

    JungleScene(boolean slope) {
        this(slope, true);
    }

    JungleScene(boolean slope, boolean tropical) {
        this.slope = slope;
        this.tropical = tropical;
    }

    public int surfaceHeight(int x, int z) {
        return 32 + (slope ? Math.floorDiv(x, 4) : 0);
    }

    public TerrainSummary sample(int x, int z) {
        return new TerrainSummary(
                surfaceHeight(x, z),
                BlockId.GRASS,
                BlockId.DIRT,
                BlockId.STONE,
                WorldBounds.MIN_Y,
                TerrainSummary.NO_WATER,
                tropical ? BiomeType.JUNGLE : BiomeType.FOREST,
                new ClimateSample(.7f, .7f));
    }

    public long seed() {
        return 42;
    }

    public java.util.List<Canopy> canopies(int ox, int oz, int size) {
        var selected = new java.util.LinkedHashSet<TreeGenerator.Tree>();
        for (int z = oz; z < oz + size; z += 16)
            for (int x = ox; x < ox + size; x += 16) selected.addAll(trees.trees(x, z));
        return selected.stream()
                .filter(t -> t.plan() != null)
                .flatMap(t -> t.plan().crowns().stream()
                        .map(c -> new Canopy(
                                c.x(),
                                c.y(),
                                c.z(),
                                c.radius(),
                                c.height(),
                                c.seed(),
                                t.plan().leaves())))
                .toList();
    }

    public int generatorVersion() {
        return 0;
    }

    public ChunkData generate(ChunkPos pos) {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                var ground = sample(pos.x() * 16 + x, pos.z() * 16 + z);
                for (int y = 0; y < 16; y++) blocks[(y * 16 + z) * 16 + x] = ground.materialAt(pos.y() * 16 + y);
            }
        var plans = trees.trees(pos.x() * 16, pos.z() * 16);
        TreeGenerator.place(plans, pos.x() * 16, pos.y() * 16, pos.z() * 16, blocks);
        JungleDecorations.generate(plans, this::sample, pos.x() * 16, pos.y() * 16, pos.z() * 16, blocks);
        jungle.generate(pos.x() * 16, pos.y() * 16, pos.z() * 16, blocks, this::sample, (x, z) -> true);
        TerrainSummary[] summaries = new TerrainSummary[256];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) summaries[z * 16 + x] = sample(pos.x() * 16 + x, pos.z() * 16 + z);
        JungleDecorations.ground(
                42, pos.x() * 16, pos.y() * 16, pos.z() * 16, blocks, TerrainColumn.of(summaries, plans), jungle);
        return new ChunkData(pos, blocks);
    }
}
