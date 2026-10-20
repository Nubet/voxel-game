package com.norbertfila.light;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldBounds;

/** Fast block reads for light searches; remembers the last chunk because searches stay local. */
final class BlockReader {
    static final int UNKNOWN = -1;
    private final World world;
    private int chunkX, chunkY, chunkZ;
    private Chunk chunk;

    BlockReader(World world) {
        this.world = world;
    }

    /** Unsigned block id, AIR above and below the world, or {@link #UNKNOWN} for a missing chunk. */
    int block(int x, int y, int z) {
        if (!WorldBounds.containsY(y)) return Byte.toUnsignedInt(BlockId.AIR);
        int cx = Math.floorDiv(x, Chunk.SIZE), cy = Math.floorDiv(y, Chunk.SIZE), cz = Math.floorDiv(z, Chunk.SIZE);
        if (chunk == null || cx != chunkX || cy != chunkY || cz != chunkZ) {
            chunk = world.getChunk(new ChunkPos(cx, cy, cz));
            chunkX = cx;
            chunkY = cy;
            chunkZ = cz;
        }
        if (chunk == null) return UNKNOWN;
        return Byte.toUnsignedInt(
                chunk.get(Math.floorMod(x, Chunk.SIZE), Math.floorMod(y, Chunk.SIZE), Math.floorMod(z, Chunk.SIZE)));
    }

    void reset() {
        chunk = null;
    }
}
