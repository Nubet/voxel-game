package com.norbertfila.diagnostics;

import com.norbertfila.render.GpuArena;
import java.util.ArrayList;
import java.util.List;

public final class GpuArenaBenchmark {
    private static final String SCENE_ID = "fixed-mesh-scene-v1";
    private static final long ALIGNMENT = 256;
    private static final long[] MESH_BYTES = {144, 1024, 4096, 16384, 65536, 262144, 1048576};

    private GpuArenaBenchmark() {}

    public static Result run() {
        long perMeshBytes = 0;
        for (long bytes : MESH_BYTES) perMeshBytes += bytes;
        long arenaCapacity = align(perMeshBytes, ALIGNMENT);

        long baselineStart = System.nanoTime();
        for (long bytes : MESH_BYTES) align(bytes, ALIGNMENT);
        long baselineNanos = System.nanoTime() - baselineStart;

        GpuArena arena = new GpuArena(arenaCapacity, ALIGNMENT);
        List<GpuArena.Allocation> allocations = new ArrayList<>();
        long arenaStart = System.nanoTime();
        for (long bytes : MESH_BYTES) allocations.add(arena.allocate(bytes));
        long arenaNanos = System.nanoTime() - arenaStart;
        for (GpuArena.Allocation allocation : allocations) arena.free(allocation);

        return new Result(
                SCENE_ID,
                MESH_BYTES.length,
                perMeshBytes,
                arenaCapacity,
                arenaCapacity - perMeshBytes,
                baselineNanos,
                arenaNanos,
                false);
    }

    public static String toJson(Result result) {
        return "{"
                + "\"sceneId\":\""
                + result.sceneId()
                + "\",\"meshCount\":"
                + result.meshCount()
                + ",\"perMeshBytes\":"
                + result.perMeshBytes()
                + ",\"arenaCapacityBytes\":"
                + result.arenaCapacityBytes()
                + ",\"arenaWasteBytes\":"
                + result.arenaWasteBytes()
                + ",\"perMeshCpuNanos\":"
                + result.perMeshCpuNanos()
                + ",\"arenaCpuNanos\":"
                + result.arenaCpuNanos()
                + ",\"gpuMeasured\":"
                + result.gpuMeasured()
                + "}";
    }

    public static void main(String[] args) {
        System.out.print(toJson(run()));
    }

    private static long align(long value, long alignment) {
        long remainder = value % alignment;
        return remainder == 0 ? value : value + alignment - remainder;
    }

    public record Result(
            String sceneId,
            int meshCount,
            long perMeshBytes,
            long arenaCapacityBytes,
            long arenaWasteBytes,
            long perMeshCpuNanos,
            long arenaCpuNanos,
            boolean gpuMeasured) {}
}
