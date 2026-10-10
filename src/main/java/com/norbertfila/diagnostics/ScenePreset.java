package com.norbertfila.diagnostics;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;

public enum ScenePreset {
    FLAT,
    HILLS,
    SOLID,
    EMPTY;

    public int generatorVersion() {
        return this == HILLS ? new HillsGenerator().generatorVersion() : 1;
    }

    public ChunkData generate(ChunkPos position, long seed) {
        if (this == HILLS) return new HillsGenerator(seed).generate(position);
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        if (this == EMPTY) return new ChunkData(position, blocks);
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    if (this == SOLID || position.y() * Chunk.SIZE + y <= 8) {
                        blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = this == SOLID ? BlockId.STONE : BlockId.GRASS;
                    }
                }
        return new ChunkData(position, blocks);
    }
}
