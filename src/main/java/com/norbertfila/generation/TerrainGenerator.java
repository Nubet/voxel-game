package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Objects;

public final class TerrainGenerator implements TerrainSource {
    private static final int GENERATOR_VERSION = 2;
    private final long seed;
    private final TerrainConfig config;
    private final Noise2D continental;
    private final Noise2D hills;
    private final Noise2D details;

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
        double height = config.baseHeight()
                + layer(continental, config.continental(), worldX, worldZ)
                + layer(hills, config.hills(), worldX, worldZ)
                + layer(details, config.details(), worldX, worldZ);
        if (!Double.isFinite(height) || height < config.minHeight() || height > config.maxHeight())
            throw new IllegalStateException("Noise must produce finite values within [-1, 1]");
        return (int) Math.floor(height);
    }

    @Override
    public TerrainSummary sample(int worldX, int worldZ) {
        return new TerrainSummary(surfaceHeight(worldX, worldZ), BlockId.GRASS, BlockId.DIRT, BlockId.STONE);
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
                int height = surfaceHeight(originX + x, originZ + z);
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int worldY = originY + y;
                    blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = worldY > height
                            ? BlockId.AIR
                            : worldY == height ? BlockId.GRASS : worldY >= height - 3 ? BlockId.DIRT : BlockId.STONE;
                }
            }
        return new ChunkData(position, blocks);
    }

    private static double layer(Noise2D noise, TerrainConfig.Layer layer, int x, int z) {
        return layer.amplitude() == 0 ? 0 : noise.sample(x, z) * layer.amplitude();
    }

    private static int origin(int chunkCoordinate) {
        int origin = Math.multiplyExact(chunkCoordinate, Chunk.SIZE);
        Math.addExact(origin, Chunk.SIZE - 1);
        return origin;
    }
}
