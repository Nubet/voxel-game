package com.norbertfila.generation;

import com.norbertfila.world.ChunkPos;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public final class CaveGenerationProfile {
    public static void main(String[] args) {
        List<ChunkPos> positions = new ArrayList<>();
        for (int z = -8; z < 8; z++)
            for (int x = -8; x < 8; x++) for (int y = -1; y <= 3; y++) positions.add(new ChunkPos(x, y, z));
        run(new TerrainGenerator(1, TerrainConfig.defaults()), positions, "warmup");
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        long cold = run(terrain, positions, "cold");
        long warm = run(terrain, positions, "warm");
        if (cold != warm) throw new AssertionError("Cache changed generated terrain");
        System.out.println("caveCache=" + terrain.caveCacheStats() + " columns=" + terrain.columnCacheStats());
    }

    private static long run(TerrainGenerator terrain, List<ChunkPos> positions, String label) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        CRC32 crc = new CRC32();
        for (var position : positions) crc.update(terrain.generate(position).blocks());
        double millis = (System.nanoTime() - start) / 1e6;
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        System.out.printf(
                "%s: chunks=%d ms=%.1f us/chunk=%.1f allocatedMiB=%.1f crc=%x%n",
                label,
                positions.size(),
                millis,
                millis * 1000 / positions.size(),
                allocated / 1048576d,
                crc.getValue());
        return crc.getValue();
    }
}
