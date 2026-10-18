package com.norbertfila.generation;

import static com.norbertfila.generation.ClassicRegionSampler.smooth;

import com.norbertfila.generation.biome.BiomeResolver;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.WorldBounds;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

final class ClassicTerrainFeatures {
    private final long seed;
    private final int sea;
    private final ClassicRegionSampler regions;
    private final PerlinNoise detail;
    private final PerlinNoise3D noise;
    private final Noise2D ground;
    private final BiPredicate<Integer, Integer> ocean;
    private final BiomeResolver biomes = new BiomeResolver();
    private final Predicate<ClassicRegionSampler.Region> eligible;
    private final GenerationCache<Boolean> eligibility = new GenerationCache<>(1024);

    ClassicTerrainFeatures(long seed, int sea, Noise2D ground, BiPredicate<Integer, Integer> ocean) {
        this(seed, sea, ground, ocean, region -> true);
    }

    ClassicTerrainFeatures(
            long seed,
            int sea,
            Noise2D ground,
            BiPredicate<Integer, Integer> ocean,
            Predicate<ClassicRegionSampler.Region> eligible) {
        this.seed = seed;
        this.sea = sea;
        this.ground = ground;
        this.ocean = ocean;
        this.eligible = eligible;
        regions = new ClassicRegionSampler(seed);
        detail = new PerlinNoise(seed ^ 0x9B05688C2B3E6C1FL);
        noise = new PerlinNoise3D(seed ^ 0x1F83D9ABFB41BD6BL);
    }

    boolean possible(int x, int z) {
        return regions.sample(x, z) != null || regions.coast(x, z) != null;
    }

    TerrainSummary apply(int x, int z, TerrainSummary base, double classicness) {
        var region = regions.sample(x, z);
        double weight = region != null
                        && eligibility.get(
                                Math.floorDiv(x, ClassicRegionSampler.CELL_SIZE),
                                Math.floorDiv(z, ClassicRegionSampler.CELL_SIZE),
                                () -> eligible.test(region))
                ? region.weight()
                : 0;
        double coastalStyle = smooth((classicness - .15) / .5);
        if (weight == 0 && coastalStyle == 0) return base;
        var coast = regions.coast(x, z);
        double coastWeight =
                coast == null ? 0 : coast.weight() * coastalStyle * coastalWeight(x, z, base.surfaceHeight());
        if (weight == 0 && coastWeight == 0) return base;
        var field = region == null ? null : new Field(region, x, z, base.surfaceHeight(), weight);
        var coastalField = coastWeight == 0 ? null : new Field(coast, x, z, base.surfaceHeight(), coastWeight);
        var selected = weight >= coastWeight ? field : coastalField;
        var profile = selected.region;
        double selectedWeight = Math.max(weight, coastWeight);
        int minY = Math.max(WorldBounds.MIN_Y + 1, Math.min(sea - 12, base.surfaceHeight() - 4));
        byte[] blocks = new byte[WorldBounds.MAX_Y_EXCLUSIVE - minY];
        int level = profile.biome() == BiomeType.HIDDEN_VALLEYS_CLASSIC && weight > .95 ? sea + 7 : base.waterLevel();
        WaterKind kind = level == sea + 7 && profile.biome() == BiomeType.HIDDEN_VALLEYS_CLASSIC
                ? WaterKind.LAKE
                : base.waterKind();
        int top = minY - 1;
        for (int y = minY; y < WorldBounds.MAX_Y_EXCLUSIVE; y++) {
            double density = coastalField == null ? base.surfaceHeight() - y : coastalField.density(y);
            if (field != null) density = field.density(y, density);
            if (density >= 0) {
                blocks[y - minY] = BlockId.STONE;
                top = y;
            } else if (y <= level) blocks[y - minY] = BlockId.WATER;
        }
        for (int y = blocks.length - 1; y >= 0; y--)
            if (blocks[y] == BlockId.STONE
                    && (y + 1 == blocks.length || blocks[y + 1] == BlockId.AIR || blocks[y + 1] == BlockId.WATER)) {
                blocks[y] = y + minY < level
                        ? BlockId.GRAVEL
                        : profile.biome() == BiomeType.COASTAL_CLIFFS_CLASSIC && y + minY <= sea + 2
                                ? BlockId.SAND
                                : BlockId.GRASS;
                for (int depth = 1; depth <= 2 && y - depth >= 0 && blocks[y - depth] == BlockId.STONE; depth++)
                    blocks[y - depth] = y + minY < level ? BlockId.GRAVEL : BlockId.DIRT;
            }
        int waterfall = waterfallTop(selected, x, z, top);
        int waterfallBottom = Math.max(top + 1, level + 1);
        if (waterfall > Math.max(top, level)) {
            for (int y = Math.max(top + 1, level + 1); y <= waterfall; y++) blocks[y - minY] = BlockId.WATER;
            level = waterfall;
            kind = WaterKind.RIVER;
        } else waterfall = Integer.MIN_VALUE;
        BiomeType biome = selectedWeight >= (profile.biome() == BiomeType.COASTAL_CLIFFS_CLASSIC ? .15 : .25)
                ? profile.biome()
                : base.biome();
        if (top < level && kind != WaterKind.NONE) biome = BiomeType.valueOf(kind.name());
        else if (biome == BiomeType.OCEAN || biome == BiomeType.LAKE || biome == BiomeType.RIVER)
            biome = biomes.resolve(base.climate(), classicness);
        var shape = new TerrainShape(
                minY, java.util.Arrays.copyOf(blocks, Math.max(top, level) - minY + 1), waterfallBottom, waterfall);
        return new TerrainSummary(
                top,
                shape.materialAt(top),
                BlockRegistry.DEFAULT.isCollidable(shape.materialAt(top - 1))
                        ? shape.materialAt(top - 1)
                        : BlockId.STONE,
                BlockId.STONE,
                base.bedrockHeight(),
                level,
                biome,
                base.climate(),
                kind,
                !biome.rareClassic() && (biome == BiomeType.TUNDRA || base.snowCover()) && top >= level,
                base.frozenWater() && top < level,
                null,
                shape);
    }

    private double coastalWeight(int x, int z, double height) {
        if (height < sea - 8 || height > sea + 60) return 0;
        double depth = 0;
        for (int[] direction :
                new int[][] {{-96, 0}, {96, 0}, {0, -96}, {0, 96}, {-96, -96}, {96, -96}, {-96, 96}, {96, 96}}) {
            long nx = (long) x + direction[0], nz = (long) z + direction[1];
            if (nx < Integer.MIN_VALUE || nx > Integer.MAX_VALUE || nz < Integer.MIN_VALUE || nz > Integer.MAX_VALUE)
                continue;
            if (ocean.test((int) nx, (int) nz)) depth = Math.max(depth, sea - ground.sample(nx, nz));
        }
        return smooth(depth / 8) * smooth((height - sea + 8) / 12) * smooth((sea + 60 - height) / 24);
    }

    private int waterfallTop(Field field, int x, int z, int top) {
        if (field.weight < .95 || field.region.biome() == BiomeType.HIDDEN_VALLEYS_CLASSIC) return Integer.MIN_VALUE;
        long hash = GenerationHash.hash(seed ^ 0x5BE0CD19137E2179L, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (Math.floorMod(hash, 4) != 0
                || Math.floorMod(x, 16) != Long.remainderUnsigned(GenerationHash.hash(hash, 1, 0), 16)
                || Math.floorMod(z, 16) != Long.remainderUnsigned(GenerationHash.hash(hash, 2, 0), 16))
            return Integer.MIN_VALUE;
        for (int[] direction : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            double nx = (double) x + direction[0], nz = (double) z + direction[1];
            var neighborRegion = regions.sample(nx, nz);
            if (field.region.biome() == BiomeType.COASTAL_CLIFFS_CLASSIC) neighborRegion = regions.coast(nx, nz);
            if (neighborRegion == null) continue;
            var neighbor = new Field(neighborRegion, nx, nz, ground.sample(nx, nz), field.weight);
            for (int y = WorldBounds.MAX_Y_EXCLUSIVE - 12; y >= top + 10; y--)
                if (neighbor.density(y) >= 0 && field.density(y) < 0) return y;
        }
        return Integer.MIN_VALUE;
    }

    final class Field {
        private final ClassicRegionSampler.Region region;
        private final double x, z, base, weight, rolling, upper, floor, islandLow, islandHigh, valleyHeight;

        Field(ClassicRegionSampler.Region region, double x, double z, double base, double weight) {
            this.region = region;
            this.x = x + 18 * detail.sample(x * .008, z * .008);
            this.z = z + 18 * detail.sample(x * .008 + 51.3, z * .008 - 19.1);
            this.base = base;
            this.weight = weight;
            rolling = detail.sample(x * .011, z * .011);
            upper = Math.min(WorldBounds.MAX_Y_EXCLUSIVE - 12, sea + 85 + 35 * rolling);
            floor = sea + 4 + 9 * detail.sample(x * .018, z * .018);
            islandLow = Math.min(WorldBounds.MAX_Y_EXCLUSIVE - 25, Math.max(sea + 65 + 24 * rolling, base + 30));
            islandHigh = Math.min(WorldBounds.MAX_Y_EXCLUSIVE - 15, islandLow + 35 + 12 * rolling);
            double u = (x - region.x()) / region.radius() + .16 * rolling;
            double v = (z - region.z()) / region.radius() * 1.2 + .12 * detail.sample(x * .009, z * .009);
            double radius = Math.hypot(u, v);
            double angle = (region.seed() >>> 11) * 0x1.0p-53 * Math.PI * 2;
            double across =
                    u * Math.cos(angle) + v * Math.sin(angle) + .45 * (-u * Math.sin(angle) + v * Math.cos(angle));
            double passage = smooth((.12 - Math.abs(across + .08 * rolling)) / .1);
            double walls = smooth((radius - .28) / .25) * (1 - .92 * passage);
            double basin = smooth((.2 - Math.hypot(u * 1.4, v)) / .1);
            valleyHeight = sea + 10 + 4 * rolling - 8 * basin + walls * (65 + 22 * rolling);
        }

        double density(double y) {
            return density(y, base - y);
        }

        double density(double y, double baseDensity) {
            if (region.biome() == BiomeType.FLOATING_ISLES_CLASSIC) {
                double band = Math.max(12 - Math.abs(y - islandLow), 9 - Math.abs(y - islandHigh));
                double islands = Math.min(band, (noise.sample(x * .022, y * .025, z * .022) - .1) * 55);
                return Math.max(baseDensity, baseDensity + (islands - baseDensity) * weight);
            }
            double target = switch (region.biome()) {
                case ARCHLANDS_CLASSIC -> {
                    double pillars = noise.sample(x * .022, y * .008, z * .022) * 70 - 5;
                    double bridges = Math.max(
                                    9 - Math.abs(y - sea - 43 - rolling * 24),
                                    6 - Math.abs(y - sea - 73 + rolling * 18))
                            + noise.sample(x * .035, y * .025, z * .035) * 25
                            + rolling * 12
                            - 5;
                    yield Math.max(floor - 7 - y, Math.min(upper - y, Math.max(pillars, bridges)));
                }
                case OVERHANG_HIGHLANDS_CLASSIC ->
                    Math.max(
                            floor - y,
                            Math.min(
                                    upper - y,
                                    noise.sample(x * .017, y * .012, z * .017) * 70
                                            + (y - sea - 38) * .25
                                            + rolling * 30
                                            - 8));
                case HIDDEN_VALLEYS_CLASSIC -> valleyHeight - y;
                case COASTAL_CLIFFS_CLASSIC -> {
                    double height = base + smooth((base - sea + 2) / 10) * (50 + 25 * rolling);
                    double opening = Math.max(
                            Math.abs(y - sea - 12) - 17, -(noise.sample(x * .038, y * .045, z * .038) - .1) * 45);
                    yield Math.min(height - y, opening);
                }
                default -> throw new IllegalStateException("Not a classic terrain profile: " + region.biome());
            };
            return baseDensity + (target - baseDensity) * weight;
        }
    }
}
