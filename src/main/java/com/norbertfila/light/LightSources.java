package com.norbertfila.light;

import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Light-emitting blocks per chunk, rescanned only when the chunk is replaced or edited. */
final class LightSources {
    private static final int CACHE_LIMIT = 4096;
    private static final int[] NONE = new int[0];
    private final World world;
    private final Map<ChunkPos, Entry> cache = new HashMap<>();

    private record Entry(Chunk chunk, long revision, int[] emitters) {}

    LightSources(World world) {
        this.world = world;
    }

    /** Emitters packed as {@code localIndex | level << 12}, or null when the chunk is not loaded. */
    int[] emitters(ChunkPos position) {
        Chunk chunk = world.getChunk(position);
        if (chunk == null) return null;
        Entry entry = cache.get(position);
        if (entry != null && entry.chunk == chunk && entry.revision == chunk.revision()) return entry.emitters;
        int[] emitters = scan(chunk);
        if (cache.size() >= CACHE_LIMIT) cache.clear();
        cache.put(position, new Entry(chunk, chunk.revision(), emitters));
        return emitters;
    }

    static int localX(int packed) {
        return packed & 15;
    }

    static int localZ(int packed) {
        return packed >> 4 & 15;
    }

    static int localY(int packed) {
        return packed >> 8 & 15;
    }

    static int level(int packed) {
        return packed >>> 12;
    }

    private static int[] scan(Chunk chunk) {
        int uniform = chunk.uniformBlock();
        if (uniform >= 0) {
            int level = BlockRegistry.DEFAULT.lightEmission((byte) uniform);
            if (level == 0) return NONE;
            int[] all = new int[Chunk.BLOCK_BYTES];
            for (int i = 0; i < all.length; i++) all[i] = i | level << 12;
            return all;
        }
        int[] found = NONE;
        int count = 0;
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    int level = BlockRegistry.DEFAULT.lightEmission(chunk.get(x, y, z));
                    if (level == 0) continue;
                    if (count == found.length) found = Arrays.copyOf(found, Math.max(16, count * 2));
                    found[count++] = (y << 8 | z << 4 | x) | level << 12;
                }
        return count == found.length ? found : Arrays.copyOf(found, count);
    }
}
