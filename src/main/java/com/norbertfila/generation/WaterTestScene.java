package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;

public final class WaterTestScene implements TerrainSource {
    @Override
    public long seed() {
        return 0;
    }

    @Override
    public int generatorVersion() {
        return 1;
    }

    @Override
    public int surfaceHeight(int x, int z) {
        return sample(x, z).surfaceHeight();
    }

    @Override
    public TerrainSummary sample(int x, int z) {
        if (x == 2 && z == -10) return new TerrainSummary(19, BlockId.STONE, BlockId.STONE, BlockId.STONE);
        boolean low = inPool(x, z) || x >= 4 && x <= 8 && z >= -12 && z <= -5;
        return new TerrainSummary(
                low ? 13 : 17,
                low ? BlockId.STONE : BlockId.GRASS,
                BlockId.DIRT,
                BlockId.STONE,
                TerrainSummary.NO_BEDROCK,
                inPool(x, z) ? 17 : TerrainSummary.NO_WATER);
    }

    @Override
    public ChunkData generate(ChunkPos position) {
        int originX = Math.multiplyExact(position.x(), Chunk.SIZE);
        int originY = Math.multiplyExact(position.y(), Chunk.SIZE);
        int originZ = Math.multiplyExact(position.z(), Chunk.SIZE);
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int worldX = Math.addExact(originX, x), worldZ = Math.addExact(originZ, z);
                TerrainSummary terrain = sample(worldX, worldZ);
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int worldY = Math.addExact(originY, y);
                    byte block = terrain.materialAt(worldY);
                    if (worldX == -3 && worldZ == -8 && worldY == 18) block = BlockId.LEAVES;
                    blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = block;
                }
            }
        return new ChunkData(position, blocks);
    }

    private static boolean inPool(int x, int z) {
        return x >= -4 && x <= 3 && z >= -12 && z <= -5;
    }
}
