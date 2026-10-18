package com.norbertfila.generation;

import com.norbertfila.generation.biome.BadlandsSurface;
import com.norbertfila.generation.biome.BiomeProfile;
import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.generation.biome.BiomeResolver;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateNoise;
import com.norbertfila.generation.biome.ClimateRules;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.generation.biome.JungleRules;
import com.norbertfila.generation.biome.SurfaceMaterialMapper;
import com.norbertfila.generation.biome.SurfaceMaterialResolver;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WorldBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class TerrainGenerator implements TerrainSource {
    private static final int GENERATOR_VERSION = 39;
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
    private final BadlandsLandforms badlandsLandforms;
    private final RegionalHydrology hydrology;
    private final SmallWaterBodyClassifier waterBodies;
    private final GenerationCache<ClimateSample> tintClimates = new GenerationCache<>(4096);
    private final GenerationCache<byte[]> tintColumns = new GenerationCache<>(2048);
    private final Noise2D shores;
    private final ClimateNoise climate;
    private final LandscapeSampler landscapes;
    private final BiomeResolver biomeResolver = new BiomeResolver();
    private final BiomeRegistry biomeRegistry = BiomeRegistry.defaults();
    private final SurfaceMaterialResolver surfaceMaterials = new SurfaceMaterialResolver(new SurfaceMaterialMapper());
    private final BadlandsSurface badlandsSurface;
    private final TreeGenerator trees;
    private final JungleVegetation jungle;
    private final GenerationCache<List<TreeGenerator.Tree>> forestPlans = new GenerationCache<>(2048);
    private final VegetationGenerator vegetation;
    private final SnowGenerator snow = new SnowGenerator();
    private final GenerationCache<TerrainColumn> columns = new GenerationCache<>(COLUMN_CACHE_CAPACITY);
    private final GenerationCache<CavePlan> cavePlans = new GenerationCache<>(128);
    private final CavePlanner cavePlanner;
    private final CaveCarver caveCarver;
    private final OreGenerator ores;

    public TerrainGenerator(long seed, TerrainConfig config) {
        this(seed, config, LandscapeConfig.defaults());
    }

    public TerrainGenerator(long seed, TerrainConfig config, LandscapeConfig landscapes) {
        this(seed, config, new PerlinNoise(seed), forestNoise(new PerlinNoise(seed)), landscapes);
    }

    public TerrainGenerator(long seed, TerrainConfig config, Noise2D noise) {
        this(seed, config, noise, forestNoise(noise));
    }

    TerrainGenerator(long seed, TerrainConfig config, Noise2D noise, Noise2D forestDensity) {
        this(seed, config, noise, forestDensity, LandscapeConfig.defaults());
    }

    TerrainGenerator(
            long seed, TerrainConfig config, Noise2D noise, Noise2D forestDensity, LandscapeConfig landscapes) {
        this.seed = seed;
        this.landscapes = new LandscapeSampler(seed, landscapes);
        badlandsSurface = new BadlandsSurface(seed);
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
        badlandsLandforms = new BadlandsLandforms(seed);
        hydrology = new RegionalHydrology(
                seed,
                waterLevel(),
                Math.min(config.maxHeight(), WorldBounds.MAX_Y_EXCLUSIVE - 1),
                (x, z) -> baseSample(x, z).elevation());
        waterBodies = new SmallWaterBodyClassifier((x, z) -> {
            var geography = geographySample(x, z);
            return geography.waterKind() == WaterKind.OCEAN && geography.elevation() < geography.waterLevel();
        });
        shores = new PerlinNoise(seed ^ 0x3C6EF372FE94F82BL);
        climate = new ClimateNoise(seed, config);
        trees = new TreeGenerator(
                seed, forestDensity, (GroundSampler) this::sampleBase, biomeRegistry, this.landscapes);
        jungle = new JungleVegetation(seed);
        vegetation = new VegetationGenerator(
                seed, new PerlinNoise(seed ^ 0xDB4F0B9175AE2165L), forestDensity, biomeRegistry, this, this.landscapes);
        cavePlanner = new CavePlanner(seed, WorldBounds.MIN_Y, this::surfaceHeight);
        caveCarver = new CaveCarver((GroundSampler) this::sampleBase);
        ores = new OreGenerator(seed);
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

    public LandscapeSample landscapeAt(double worldX, double worldZ) {
        return landscapes.sample(worldX, worldZ);
    }

    @Override
    public List<Canopy> canopies(int originX, int originZ, int size) {
        if (size < 1 || size > 1024) throw new IllegalArgumentException("Invalid canopy query size");
        long endX = (long) originX + size - 1, endZ = (long) originZ + size - 1;
        if (endX > Integer.MAX_VALUE || endZ > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Canopy query overflow");
        var trees = new java.util.LinkedHashSet<TreeGenerator.Tree>();
        for (int z = Math.floorDiv(originZ, 16); z <= Math.floorDiv((int) endZ, 16); z++)
            for (int x = Math.floorDiv(originX, 16); x <= Math.floorDiv((int) endX, 16); x++) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                trees.addAll(forestAt(x, z));
            }
        var result = new ArrayList<Canopy>();
        for (var tree : trees) {
            if (tree.plan() == null) continue;
            for (var crown : tree.plan().crowns())
                if ((long) crown.x() + crown.radius() >= originX
                        && (long) crown.x() - crown.radius() <= endX
                        && (long) crown.z() + crown.radius() >= originZ
                        && (long) crown.z() - crown.radius() <= endZ)
                    result.add(new Canopy(
                            crown.x(),
                            crown.y(),
                            crown.z(),
                            crown.radius(),
                            crown.height(),
                            crown.seed(),
                            tree.plan().leaves()));
        }
        return List.copyOf(result);
    }

    @Override
    public int tintIndex(int x, int z) {
        int gx = Math.floorDiv(x, 64), gz = Math.floorDiv(z, 64);
        return com.norbertfila.generation.biome.BiomeTint.blend(
                tintClimate(gx, gz),
                tintClimate(gx + 1, gz),
                tintClimate(gx, gz + 1),
                tintClimate(gx + 1, gz + 1),
                Math.floorMod(x, 64) / 64.0,
                Math.floorMod(z, 64) / 64.0);
    }

    private ClimateSample tintClimate(int x, int z) {
        return tintClimates.get(x, z, () -> {
            var ground = sample((int) Math.clamp((long) x * 64, Integer.MIN_VALUE, Integer.MAX_VALUE), (int)
                    Math.clamp((long) z * 64, Integer.MIN_VALUE, Integer.MAX_VALUE));
            return new ClimateSample(
                    Math.clamp(
                            ClimateRules.temperature(ground.climate(), ground.surfaceHeight(), config.baseHeight()),
                            -1,
                            1),
                    ground.climate().humidity());
        });
    }

    private ChunkData tinted(ChunkData data, int ox, int oz) {
        byte[] tint = tintColumns.get(Math.floorDiv(ox, Chunk.SIZE), Math.floorDiv(oz, Chunk.SIZE), () -> {
            byte[] result = new byte[Chunk.SIZE * Chunk.SIZE];
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) result[z * Chunk.SIZE + x] = (byte) tintIndex(ox + x, oz + z);
            return result;
        });
        return data.withTints(tint);
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
        int height = (int) Math.floor(geography.elevation());
        WaterKind waterKind = geography.waterKind();
        if (waterKind == WaterKind.OCEAN && height < geography.waterLevel())
            waterKind = waterBodies.classify((int) worldX, (int) worldZ);
        double slope = surfaceSlope((int) worldX, (int) worldZ, geography.elevation());
        boolean nearWater = nearWater((int) worldX, (int) worldZ, height, geography.waterLevel());
        BiomeType biome = biomeResolver.resolve(
                climateSample, height, config.baseHeight(), slope, nearWater, geography.waterLevel(), waterKind);
        return new ColumnSample(
                geography.elevation(),
                climateSample,
                biome,
                biomeRegistry.profile(biome),
                geography.waterLevel(),
                waterKind,
                slope,
                nearWater);
    }

    private GeographySample geographySample(double worldX, double worldZ) {
        GeographySample base = baseSample(worldX, worldZ);
        if (config.landforms().amplitude() == 0) return base;
        boolean rivers = biomeResolver.resolve(climate.sample(base.x(), base.z())) != BiomeType.DESERT;
        var water = hydrology.sample(worldX, worldZ, base.elevation(), rivers);
        return new GeographySample(boundedHeight(water.height()), base.x(), base.z(), water.waterLevel(), water.kind());
    }

    private GeographySample baseSample(double worldX, double worldZ) {
        var landscape = landscapes.sample(
                Math.clamp(worldX, Integer.MIN_VALUE, Integer.MAX_VALUE),
                Math.clamp(worldZ, Integer.MIN_VALUE, Integer.MAX_VALUE));
        double x = worldX;
        double z = worldZ;
        if (config.warp().amplitude() != 0) {
            x += layer(warpX, config.warp(), worldX, worldZ) * landscape.warp();
            z += layer(warpZ, config.warp(), worldX, worldZ) * landscape.warp();
        }
        double continentalLayer = layer(continental, config.continental(), x, z);
        RegionalLandforms.Sample region = landforms.sample(x, z);
        double regionalElevation = region.elevation() * landscape.elevation();
        double detailScale = region.details() + (1 - region.details()) * landscape.classicness();
        double height = config.baseHeight()
                + continentalLayer
                + regionalElevation
                + region.hills() * landscape.hills() * layer(hills, config.hills(), x, z)
                + detailScale * layer(details, config.details(), x, z)
                + mountainHeight(x, z)
                        * landscape.mountains()
                        * (1 - .85 * (1 - landscape.classicness()) * JungleRules.climateWeight(climate.sample(x, z)));
        if (config.landforms().amplitude() > 0) {
            var weather = climate.sample(x, z);
            double aridity = mountainMask((weather.temperature() - .1) / .4, 0, 1)
                    * mountainMask((.2 - weather.humidity()) / .4, 0, 1);
            double dunes = 3 * Math.sin(x * .035 + 2 * Math.sin(z * .006)) * aridity;
            double terrace = 4 * Math.sin(height * Math.PI / 8) * aridity;
            height +=
                    Math.clamp((dunes + terrace) / 7, -1, 1) * config.details().amplitude()
                            - detailScale * layer(details, config.details(), x, z);
            double regionalHeight = config.baseHeight() + continentalLayer + regionalElevation;
            height = Math.max(height, Math.min(regionalHeight - 3, config.waterLevel() + 1.0));
            double badlandsWeight = BadlandsLandforms.weight(weather)
                    * (1 - .75 * landscape.classicness())
                    * mountainMask(regionalHeight, waterLevel() + 2.0, waterLevel() + 18.0);
            if (badlandsWeight > 0) {
                double eroded = Math.max(
                        waterLevel() + 2.0,
                        config.baseHeight()
                                + continentalLayer
                                + config.landforms().amplitude() * badlandsLandforms.sample(x, z));
                height += badlandsWeight * (eroded - height);
            }
        }
        if (!Double.isFinite(height) || height < config.minHeight() || height > config.maxHeight())
            throw new IllegalStateException("Noise must produce finite values within [-1, 1]");
        return new GeographySample(boundedHeight(height), x, z, waterLevel(), WaterKind.OCEAN);
    }

    private static double boundedHeight(double height) {
        return Math.clamp(height, WorldBounds.MIN_Y + 1, WorldBounds.MAX_Y_EXCLUSIVE - 1);
    }

    private int waterLevel() {
        return (int) boundedHeight(config.waterLevel());
    }

    @Override
    public TerrainSummary sample(int worldX, int worldZ) {
        var base = sampleBase(worldX, worldZ);
        if (base.biome() != BiomeType.JUNGLE) return base;
        byte surface =
                jungle.surface(worldX, worldZ, base, forestAt(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16)));
        return new TerrainSummary(
                base.surfaceHeight(),
                surface,
                base.subsurfaceMaterial(),
                base.deepMaterial(),
                base.bedrockHeight(),
                base.waterLevel(),
                base.biome(),
                base.climate(),
                base.waterKind(),
                base.snowCover());
    }

    private TerrainSummary sampleBase(int worldX, int worldZ) {
        ColumnSample column = columnSample(worldX, worldZ);
        int height = (int) Math.floor(column.elevation());
        double slope = column.slope();
        var materials = surfaceMaterials.resolve(
                height,
                slope,
                column.waterLevel(),
                config.baseHeight(),
                column.climate(),
                column.profile(),
                column.waterKind(),
                column.nearWater(),
                shores.sample(worldX * .008, worldZ * .008));
        if ((column.biome() == BiomeType.TAIGA || column.biome() == BiomeType.SNOWY_TAIGA)
                && height > column.waterLevel() + 1
                && slope <= 1.5) {
            materials = surfaceMaterials.forestFloor(shores.sample(worldX * .035, worldZ * .035));
        }
        var badlands = column.biome() == BiomeType.BADLANDS ? badlandsSurface.column(worldX, worldZ) : null;
        byte surface = badlands == null ? materials.surface() : badlands.surfaceAt(height, waterLevel());
        byte subsurface = badlands != null
                ? badlands.materialAt(height - 1, height, surface)
                : column.biome() == BiomeType.DESERT ? BlockId.SANDSTONE : materials.subsurface();
        return new TerrainSummary(
                height,
                surface,
                subsurface,
                BlockId.STONE,
                WorldBounds.MIN_Y,
                column.waterLevel(),
                column.biome(),
                column.climate(),
                column.waterKind(),
                height >= column.waterLevel()
                        && com.norbertfila.generation.biome.SnowRules.covered(
                                column.biome(),
                                com.norbertfila.generation.biome.ClimateRules.temperature(
                                        column.climate(), height, config.baseHeight()),
                                slope,
                                shores.sample(worldX * .018, worldZ * .018)),
                height < column.waterLevel()
                        && com.norbertfila.generation.biome.SnowRules.freezes(
                                column.waterKind(),
                                com.norbertfila.generation.biome.ClimateRules.temperature(
                                        column.climate(), column.waterLevel(), config.baseHeight())),
                badlands);
    }

    private boolean nearWater(int x, int z, int height, int level) {
        if (height < level) return true;
        if ((long) height - level > 2) return false;
        for (int[] direction : new int[][] {{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
            var neighbor = geographySample((double) x + direction[0], (double) z + direction[1]);
            if (neighbor.elevation() < neighbor.waterLevel()) return true;
        }
        return false;
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
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        int originX = origin(position.x());
        int originY = origin(position.y());
        int originZ = origin(position.z());
        if (position.y() < WorldBounds.MIN_CHUNK_Y || position.y() > WorldBounds.MAX_CHUNK_Y)
            return ChunkData.uniform(position, BlockId.AIR);
        TerrainColumn column = columns.get(position.x(), position.z(), () -> column(originX, originZ));
        int uniform = column.uniformBlock(originY);
        boolean mayHaveOres = (uniform < 0 || uniform == BlockId.STONE) && ores.mayGenerate(originY);
        if (uniform >= 0
                && !mayHaveOres
                && (!CaveCarver.carveable((byte) uniform)
                        || column.caves().stream()
                                .noneMatch(s -> s.y() + s.radiusY() > originY
                                        && s.y() - s.radiusY() < (double) originY + Chunk.SIZE)))
            return tinted(ChunkData.uniform(position, (byte) uniform), originX, originZ);
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
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        boolean mineralized = mayHaveOres && ores.generate(position, blocks);
        if (uniform >= 0 && !carved && !mineralized)
            return tinted(ChunkData.uniform(position, (byte) uniform), originX, originZ);
        TreeGenerator.place(column.trees(), originX, originY, originZ, blocks);
        boolean hasJungle = false;
        for (int i = 0; i < TerrainColumn.AREA && !hasJungle; i++) hasJungle = column.biome(i) == BiomeType.JUNGLE;
        JungleDecorations.generate(column.trees(), this::sampleBase, originX, originY, originZ, blocks);
        if (hasJungle) {
            jungle.generate(originX, originY, originZ, blocks, this::sampleBase, this::surfaceSupported);
            JungleDecorations.ground(seed, originX, originY, originZ, blocks, column, jungle);
        }
        snow.generate(originX, originY, originZ, blocks, column);
        vegetation.generate(originX, originY, originZ, blocks, column);
        return tinted(new ChunkData(position, blocks), originX, originZ);
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
        var supportedTrees = forestAt(Math.floorDiv(originX, 16), Math.floorDiv(originZ, 16));
        return TerrainColumn.of(summaries, supportedTrees, segments, removedSurface);
    }

    private boolean treeSupported(TreeGenerator.Tree tree) {
        int width = tree.type() == com.norbertfila.generation.biome.TreeType.GIANT_JUNGLE ? 2 : 1;
        for (int dz = 0; dz < width; dz++)
            for (int dx = 0; dx < width; dx++) {
                int x = tree.x() + dx, z = tree.z() + dz;
                if (!surfaceSupported(x, z)) return false;
            }
        return true;
    }

    private boolean surfaceSupported(int x, int z) {
        var ground = sampleBase(x, z);
        return !caveCarver.carves(
                x,
                ground.surfaceHeight(),
                z,
                ground.surfaceMaterial(),
                cavesAt(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
    }

    private List<TreeGenerator.Tree> forestAt(int chunkX, int chunkZ) {
        return forestPlans.get(
                chunkX,
                chunkZ,
                () -> trees.trees(chunkX * 16, chunkZ * 16).stream()
                        .filter(this::treeSupported)
                        .toList());
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
            WaterKind waterKind,
            double slope,
            boolean nearWater) {}
}
