package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Objects;

public final class TerrainGenerator implements TerrainSource {
    private static final int GENERATOR_VERSION = 6;
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

    public TerrainGenerator(long seed, TerrainConfig config) {
        this(seed, config, new PerlinNoise(seed));
    }

    public TerrainGenerator(long seed, TerrainConfig config, Noise2D noise) {
        this.seed = seed;
        this.config = Objects.requireNonNull(config);
        Objects.requireNonNull(noise);
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
        return (int) Math.floor(columnSample(worldX, worldZ).elevation());
    }

    double surfaceElevation(double worldX, double worldZ) {
        return columnSample(worldX, worldZ).elevation();
    }

    private ColumnSample columnSample(double worldX, double worldZ) {
        double x = worldX;
        double z = worldZ;
        if (config.warp().amplitude() != 0) {
            x += layer(warpX, config.warp(), worldX, worldZ);
            z += layer(warpZ, config.warp(), worldX, worldZ);
        }
        double continentalLayer = layer(continental, config.continental(), x, z);
        double height = config.baseHeight()
                + continentalLayer
                + layer(hills, config.hills(), x, z)
                + layer(details, config.details(), x, z)
                + mountainHeight(x, z);
        if (!Double.isFinite(height) || height < config.minHeight() || height > config.maxHeight())
            throw new IllegalStateException("Noise must produce finite values within [-1, 1]");
        double biome = config.continental().amplitude() == 0
                ? 0
                : continentalLayer / config.continental().amplitude();
        return new ColumnSample(height, biome);
    }

    @Override
    public TerrainSummary sample(int worldX, int worldZ) {
        ColumnSample column = columnSample(worldX, worldZ);
        int height = (int) Math.floor(column.elevation());
        double biome = column.biome();
        byte surface = height >= config.baseHeight() + 70 ? BlockId.SNOW : biome < -.1 ? BlockId.SAND : BlockId.GRASS;
        byte subsurface = surface == BlockId.SAND ? surface : BlockId.DIRT;
        return new TerrainSummary(height, surface, subsurface, BlockId.STONE, config.minHeight() - 1);
    }

    @Override
    public ChunkData generate(ChunkPos position) {
        Objects.requireNonNull(position);
        int originX = origin(position.x());
        int originY = origin(position.y());
        int originZ = origin(position.z());
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                TerrainSummary summary = sample(originX + x, originZ + z);
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int worldY = originY + y;
                    blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = summary.materialAt(worldY);
                }
            }
        generateTrees(originX, originY, originZ, blocks);
        return new ChunkData(position, blocks);
    }

    private void generateTrees(int originX, int originY, int originZ, byte[] blocks) {
        for (long anchorZ = (long) originZ - 2; anchorZ <= (long) originZ + Chunk.SIZE + 1; anchorZ++)
            for (long anchorX = (long) originX - 2; anchorX <= (long) originX + Chunk.SIZE + 1; anchorX++) {
                if (anchorX < Integer.MIN_VALUE
                        || anchorX > Integer.MAX_VALUE
                        || anchorZ < Integer.MIN_VALUE
                        || anchorZ > Integer.MAX_VALUE) continue;
                int x = (int) anchorX, z = (int) anchorZ;
                if (Math.floorMod(treeHash(seed, x, z), 64) != 0) continue;
                TerrainSummary terrain = sample(x, z);
                if (terrain.surfaceMaterial() == BlockId.SAND || terrain.surfaceMaterial() == BlockId.SNOW) continue;
                int baseY = terrain.surfaceHeight() + 1;
                for (int y = baseY; y < baseY + 4; y++)
                    placeTreeBlock(x, y, z, originX, originY, originZ, blocks, BlockId.LOG);
                for (int y = baseY + 3; y <= baseY + 5; y++) {
                    int radius = y == baseY + 5 ? 1 : 2;
                    for (int dz = -radius; dz <= radius; dz++)
                        for (int dx = -radius; dx <= radius; dx++) {
                            if (Math.abs(dx) + Math.abs(dz) > radius + 1 || dx == 0 && dz == 0) continue;
                            if ((long) x + dx < Integer.MIN_VALUE
                                    || (long) x + dx > Integer.MAX_VALUE
                                    || (long) z + dz < Integer.MIN_VALUE
                                    || (long) z + dz > Integer.MAX_VALUE) continue;
                            placeTreeBlock(x + dx, y, z + dz, originX, originY, originZ, blocks, BlockId.LEAVES);
                        }
                }
            }
    }

    private static void placeTreeBlock(
            int worldX, int worldY, int worldZ, int originX, int originY, int originZ, byte[] blocks, byte block) {
        int x = worldX - originX, y = worldY - originY, z = worldZ - originZ;
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) return;
        int index = (y * Chunk.SIZE + z) * Chunk.SIZE + x;
        if (blocks[index] == BlockId.AIR) blocks[index] = block;
    }

    private static long treeHash(long seed, int x, int z) {
        long value = (seed ^ 0x6A09E667F3BCC909L) ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
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

    private record ColumnSample(double elevation, double biome) {}
}
