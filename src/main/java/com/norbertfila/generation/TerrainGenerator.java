package com.norbertfila.generation;

import com.norbertfila.generation.biome.BiomeProfile;
import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.generation.biome.BiomeResolver;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateNoise;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.generation.biome.SurfaceMaterialMapper;
import com.norbertfila.generation.biome.SurfaceMaterialResolver;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class TerrainGenerator implements TerrainSource {
    private static final int GENERATOR_VERSION = 18;
    // Covers every column within render distance 16 plus movement margin, so all sections of a column hit the cache.
    private static final int COLUMN_CACHE_CAPACITY = 2048;
    private final long seed;
    private final TerrainConfig config;
    private final Noise2D continental;
    private final Noise2D hills;
    private final Noise2D details;
    private final Noise2D regions;
    private final Noise2D ridges;
    private final Noise2D ranges;
    private final Noise2D warpX;
    private final Noise2D warpZ;
    private final RegionalLandforms landforms;
    private final RegionalHydrology hydrology;
    private final ClimateNoise climate;
    private final BiomeResolver biomeResolver = new BiomeResolver();
    private final BiomeRegistry biomeRegistry = BiomeRegistry.defaults();
    private final SurfaceMaterialResolver surfaceMaterials = new SurfaceMaterialResolver(new SurfaceMaterialMapper());
    private final TreeGenerator trees;
    private final VegetationGenerator vegetation;
    private final GenerationCache<TerrainColumn> columns = new GenerationCache<>(COLUMN_CACHE_CAPACITY);
    private final GenerationCache<CavePlan> cavePlans = new GenerationCache<>(128);
    private final CavePlanner cavePlanner;
    private final CaveCarver caveCarver;

    public TerrainGenerator(long seed, TerrainConfig config) {
        this(seed, config, new PerlinNoise(seed));
    }

    public TerrainGenerator(long seed, TerrainConfig config, Noise2D noise) {
        this(seed, config, noise, forestNoise(noise));
    }

    TerrainGenerator(long seed, TerrainConfig config, Noise2D noise, Noise2D forestDensity) {
        this.seed = seed;
        this.config = Objects.requireNonNull(config);
        Objects.requireNonNull(noise);
        Objects.requireNonNull(forestDensity);
        continental = config.continental().fractal(noise);
        hills = config.hills().fractal(noise);
        details = config.details().fractal(noise);
        regions = config.mountains().region().fractal(noise);
        ridges = config.mountains()
                .ridge()
                .fractal(
                        config.mountains().perOctaveRidges()
                                ? (x, z) -> ridged(checked(noise.sample(x + 37.7, z - 91.1)))
                                : noise);
        ranges = config.mountains().ranges().fractal((x, z) -> noise.sample(x - 73.3, z + 29.9));
        warpX = config.warp().fractal((x, z) -> noise.sample(x + 17.1, z + 53.7));
        warpZ = config.warp().fractal((x, z) -> noise.sample(x + 121.7, z - 31.2));
        landforms = new RegionalLandforms(config.landforms(), noise);
        hydrology = new RegionalHydrology(
                seed, config.waterLevel(), (x, z) -> baseSample(x, z).elevation());
        climate = new ClimateNoise(seed, config);
        trees = new TreeGenerator(seed, forestDensity, this, biomeRegistry);
        vegetation = new VegetationGenerator(
                seed, new PerlinNoise(seed ^ 0xDB4F0B9175AE2165L), forestDensity, biomeRegistry);
        cavePlanner = new CavePlanner(seed, config.minHeight() - 1, this::surfaceHeight);
        caveCarver = new CaveCarver(this);
    }

    private static Noise2D forestNoise(Noise2D noise) {
        return (x, z) -> noise.sample(x + .5, z + .5);
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public int generatorVersion() {
        return GENERATOR_VERSION;
    }

    @Override
    public int surfaceHeight(int worldX, int worldZ) {
        return (int) Math.floor(geographySample(worldX, worldZ).elevation());
    }

    double surfaceElevation(double worldX, double worldZ) {
        return geographySample(worldX, worldZ).elevation();
    }

    private ColumnSample columnSample(double worldX, double worldZ) {
        GeographySample geography = geographySample(worldX, worldZ);
        ClimateSample climateSample = climate.sample(geography.x(), geography.z());
        BiomeType biome = biomeResolver.resolve(climateSample);
        return new ColumnSample(
                geography.elevation(),
                climateSample,
                biome,
                biomeRegistry.profile(biome),
                geography.waterLevel(),
                geography.waterKind());
    }

    private GeographySample geographySample(double worldX, double worldZ) {
        GeographySample base = baseSample(worldX, worldZ);
        if (config.landforms().amplitude() == 0) return base;
        var water = hydrology.sample(worldX, worldZ, base.elevation());
        return new GeographySample(water.height(), base.x(), base.z(), water.waterLevel(), water.kind());
    }

    private GeographySample baseSample(double worldX, double worldZ) {
        double x = worldX;
        double z = worldZ;
        if (config.warp().amplitude() != 0) {
            x += layer(warpX, config.warp(), worldX, worldZ);
            z += layer(warpZ, config.warp(), worldX, worldZ);
        }
        double continentalLayer = layer(continental, config.continental(), x, z);
        RegionalLandforms.Sample region = landforms.sample(x, z);
        double height = config.baseHeight()
                + continentalLayer
                + region.elevation()
                + region.hills() * layer(hills, config.hills(), x, z)
                + region.details() * layer(details, config.details(), x, z)
                + mountainHeight(x, z);
        if (!Double.isFinite(height) || height < config.minHeight() || height > config.maxHeight())
            throw new IllegalStateException("Noise must produce finite values within [-1, 1]");
        return new GeographySample(height, x, z, config.waterLevel(), WaterKind.OCEAN);
    }

    @Override
    public TerrainSummary sample(int worldX, int worldZ) {
        ColumnSample column = columnSample(worldX, worldZ);
        int height = (int) Math.floor(column.elevation());
        double slope = surfaceSlope(worldX, worldZ, column.elevation());
        var materials = surfaceMaterials.resolve(
                height, slope, column.waterLevel(), config.baseHeight(), column.climate(), column.profile());
        return new TerrainSummary(
                height,
                materials.surface(),
                materials.subsurface(),
                BlockId.STONE,
                config.minHeight() - 1,
                column.waterLevel(),
                column.biome(),
                column.climate(),
                column.waterKind());
    }

    private double surfaceSlope(int worldX, int worldZ, double elevation) {
        return Math.max(
                Math.max(
                        Math.abs(surfaceElevation((double) worldX - 1, worldZ) - elevation),
                        Math.abs(surfaceElevation((double) worldX + 1, worldZ) - elevation)),
                Math.max(
                        Math.abs(surfaceElevation(worldX, (double) worldZ - 1) - elevation),
                        Math.abs(surfaceElevation(worldX, (double) worldZ + 1) - elevation)));
    }

    @Override
    public ChunkData generate(ChunkPos position) {
        Objects.requireNonNull(position);
        int originX = origin(position.x());
        int originY = origin(position.y());
        int originZ = origin(position.z());
        TerrainColumn column = columns.get(position.x(), position.z(), () -> column(originX, originZ));
        int uniform = column.uniformBlock(originY);
        if (uniform >= 0
                && (!CaveCarver.carveable((byte) uniform)
                        || column.caves().stream()
                                .noneMatch(s -> s.y() + s.radiusY() > originY
                                        && s.y() - s.radiusY() < (double) originY + Chunk.SIZE)))
            return ChunkData.uniform(position, (byte) uniform);
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        if (uniform >= 0) Arrays.fill(blocks, (byte) uniform);
        else
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    int index = z * Chunk.SIZE + x;
                    for (int y = 0; y < Chunk.SIZE; y++)
                        blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = column.materialAt(index, originY + y);
                }
        boolean carved = caveCarver.carve(position, blocks, column.caves());
        if (uniform >= 0 && !carved) return ChunkData.uniform(position, (byte) uniform);
        TreeGenerator.place(column.trees(), originX, originY, originZ, blocks);
        vegetation.generate(originX, originY, originZ, blocks, column);
        return new ChunkData(position, blocks);
    }

    private TerrainColumn column(int originX, int originZ) {
        TerrainSummary[] summaries = new TerrainSummary[TerrainColumn.AREA];
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) summaries[z * Chunk.SIZE + x] = sample(originX + x, originZ + z);
        var segments = cavesAt(originX / Chunk.SIZE, originZ / Chunk.SIZE);
        var removedSurface = new BitSet(TerrainColumn.AREA);
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int index = z * Chunk.SIZE + x;
                var summary = summaries[index];
                if (caveCarver.carves(
                        originX + x, summary.surfaceHeight(), originZ + z, summary.surfaceMaterial(), segments))
                    removedSurface.set(index);
            }
        var supportedTrees = trees.trees(originX, originZ).stream()
                .filter(tree -> !caveCarver.carves(
                        tree.x(),
                        tree.baseY() - 1,
                        tree.z(),
                        BlockId.GRASS,
                        cavesAt(Math.floorDiv(tree.x(), Chunk.SIZE), Math.floorDiv(tree.z(), Chunk.SIZE))))
                .toList();
        return TerrainColumn.of(summaries, supportedTrees, segments, removedSurface);
    }

    private List<CavePlan.Segment> cavesAt(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, CavePlanner.REGION_SIZE / Chunk.SIZE);
        int regionZ = Math.floorDiv(chunkZ, CavePlanner.REGION_SIZE / Chunk.SIZE);
        List<CavePlan.Segment> segments = new ArrayList<>();
        for (int z = regionZ - 1; z <= regionZ + 1; z++)
            for (int x = regionX - 1; x <= regionX + 1; x++) {
                int rx = x, rz = z;
                segments.addAll(
                        cavePlans.get(rx, rz, () -> cavePlanner.plan(rx, rz)).segments(chunkX, chunkZ));
            }
        return List.copyOf(segments);
    }

    GenerationCache.Stats columnCacheStats() {
        return columns.stats();
    }

    GenerationCache.Stats caveCacheStats() {
        return cavePlans.stats();
    }

    private static double layer(Noise2D noise, TerrainConfig.Layer layer, double x, double z) {
        return layer.amplitude() == 0 ? 0 : checked(noise.sample(x, z)) * layer.amplitude();
    }

    private double mountainHeight(double x, double z) {
        TerrainConfig.Mountains mountains = config.mountains();
        if (mountains.ridge().amplitude() == 0) return 0;
        double region = (layer(regions, mountains.region(), x, z) + 1) * .5;
        double mask = mountainMask(region, mountains.maskStart(), mountains.maskEnd());
        if (mask == 0) return 0;
        double ridge = checked(ridges.sample(x, z));
        if (!mountains.perOctaveRidges()) ridge = 1 - Math.abs(ridge);
        double massif = mountains.ranges().amplitude() == 0
                ? 1
                : Math.clamp(.75 + 2 * layer(ranges, mountains.ranges(), x, z), 0, 1);
        double shapedRidge =
                mountains.ridgeExponent() == 2 ? ridge * ridge : Math.pow(ridge, mountains.ridgeExponent());
        // A broad base joins sharp octave ridges into massifs instead of isolated spires.
        if (mountains.perOctaveRidges()) shapedRidge = .25 + .75 * shapedRidge;
        return shapedRidge
                * Math.pow(massif, mountains.rangeExponent())
                * mask
                * mountains.ridge().amplitude();
    }

    static double ridged(double value) {
        return 1 - Math.min(1, Math.abs(value) * 2);
    }

    private static double checked(double value) {
        if (!Double.isFinite(value) || value < -1 || value > 1)
            throw new IllegalStateException("Noise must produce finite values within [-1, 1]");
        return value;
    }

    static double mountainMask(double region, double start, double end) {
        double t = Math.clamp((region - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static int origin(int chunkCoordinate) {
        int origin = Math.multiplyExact(chunkCoordinate, Chunk.SIZE);
        Math.addExact(origin, Chunk.SIZE - 1);
        return origin;
    }

    private record GeographySample(double elevation, double x, double z, int waterLevel, WaterKind waterKind) {}

    private record ColumnSample(
            double elevation,
            ClimateSample climate,
            BiomeType biome,
            BiomeProfile profile,
            int waterLevel,
            WaterKind waterKind) {}
}
