package com.norbertfila.light;

import com.norbertfila.world.BlockMutation;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldBounds;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/** Highest block that stops sky light in each column, cached until a block or chunk in the column changes. */
public final class SkyExposure {
    public static final int OPEN = WorldBounds.MIN_Y - 1;
    private static final int CACHE_LIMIT = 1 << 16;
    private final World world;
    private final Map<Long, Integer> tops = new HashMap<>();

    public SkyExposure(World world) {
        this.world = Objects.requireNonNull(world);
    }

    /** Y of the highest light-blocking block, {@link #OPEN} for a clear column, empty when a chunk is missing. */
    public OptionalInt top(int x, int z) {
        world.assertOwner();
        Integer cached = tops.get(key(x, z));
        if (cached != null) return OptionalInt.of(cached);
        OptionalInt scanned = scan(x, z);
        if (scanned.isPresent()) {
            if (tops.size() >= CACHE_LIMIT) tops.clear();
            tops.put(key(x, z), scanned.getAsInt());
        }
        return scanned;
    }

    public void onBlockChanged(BlockMutation mutation) {
        tops.remove(key(mutation.position().x(), mutation.position().z()));
    }

    public void onChunkChanged(ChunkPos chunk) {
        int originX = chunk.x() * Chunk.SIZE, originZ = chunk.z() * Chunk.SIZE;
        for (int dz = 0; dz < Chunk.SIZE; dz++)
            for (int dx = 0; dx < Chunk.SIZE; dx++) tops.remove(key(originX + dx, originZ + dz));
    }

    private OptionalInt scan(int x, int z) {
        int cx = Math.floorDiv(x, Chunk.SIZE), cz = Math.floorDiv(z, Chunk.SIZE);
        int lx = Math.floorMod(x, Chunk.SIZE), lz = Math.floorMod(z, Chunk.SIZE);
        for (int cy = WorldBounds.MAX_CHUNK_Y; cy >= WorldBounds.MIN_CHUNK_Y; cy--) {
            var chunk = world.getChunk(new ChunkPos(cx, cy, cz));
            if (chunk == null) return OptionalInt.empty();
            int uniform = chunk.uniformBlock();
            if (uniform >= 0 && BlockRegistry.DEFAULT.transmitsLight((byte) uniform)) continue;
            for (int ly = Chunk.SIZE - 1; ly >= 0; ly--)
                if (!BlockRegistry.DEFAULT.transmitsLight(chunk.get(lx, ly, lz)))
                    return OptionalInt.of(cy * Chunk.SIZE + ly);
        }
        return OptionalInt.of(OPEN);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(z);
    }
}
