package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
        describeUnderground(terrain);
    }

    private static void describeUnderground(TerrainGenerator terrain) {
        int[] profiles = new int[CaveProfile.values().length];
        var planner = new CavePlanner(terrain.seed(), -64, terrain::surfaceHeight);
        var features = new HashSet<CaveFeatures.Feature>();
        for (int rz = -2; rz <= 2; rz++)
            for (int rx = -2; rx <= 2; rx++) {
                for (var segment : planner.plan(rx, rz).allSegments())
                    profiles[segment.profile().ordinal()]++;
                for (int z = rz * 8; z < rz * 8 + 8; z++)
                    for (int x = rx * 8; x < rx * 8 + 8; x++) features.addAll(terrain.caveFeatures(x, z));
            }
        long lava = features.stream().filter(f -> f.material() == BlockId.LAVA).count();
        long pools = features.stream()
                .filter(f -> f.type() == CaveFeatures.Type.POOL)
                .count();
        long springs = features.stream()
                .filter(f -> f.type() == CaveFeatures.Type.SPRING)
                .count();
        long flooded = features.stream()
                .filter(f -> f.type() == CaveFeatures.Type.FLOODED)
                .count();
        long rubble = features.stream()
                .filter(f -> f.type() == CaveFeatures.Type.RUBBLE)
                .count();
        long sinkholes = 0;
        for (int rz = -2; rz <= 2; rz++)
            for (int rx = -2; rx <= 2; rx++)
                sinkholes += planner.plan(rx, rz).systems().stream()
                        .mapToLong(system -> system.sinkholes().size())
                        .sum();
        System.out.println("profiles " + Arrays.toString(CaveProfile.values()) + "=" + Arrays.toString(profiles)
                + " flooded=" + flooded + " features=" + features.size() + " pools=" + pools + " lavaPools=" + lava
                + " springs=" + springs + " rubble=" + rubble + " sinkholes=" + sinkholes);
        List<ChunkPos> deep = new ArrayList<>();
        for (int z = -8; z < 8; z++)
            for (int x = -8; x < 8; x++) for (int y = -4; y <= -2; y++) deep.add(new ChunkPos(x, y, z));
        long first = run(terrain, deep, "deep-first");
        long repeat = run(terrain, deep, "deep-repeat");
        if (first != repeat || lava == 0 || pools == 0)
            throw new AssertionError("Missing or nondeterministic cave features");
    }

    private static long run(TerrainGenerator terrain, List<ChunkPos> positions, String label) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        CRC32 crc = new CRC32();
        long[] durations = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            long chunkStart = System.nanoTime();
            var data = terrain.generate(positions.get(i));
            durations[i] = System.nanoTime() - chunkStart;
            crc.update(data.blocks());
        }
        double millis = (System.nanoTime() - start) / 1e6;
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        Arrays.sort(durations);
        System.out.printf(
                "%s: chunks=%d ms=%.1f us/chunk=%.1f p50us=%.1f p95us=%.1f allocatedMiB=%.1f crc=%x%n",
                label,
                positions.size(),
                millis,
                millis * 1000 / positions.size(),
                durations[durations.length / 2] / 1000d,
                durations[(int) (durations.length * .95)] / 1000d,
                allocated / 1048576d,
                crc.getValue());
        return crc.getValue();
    }
}
