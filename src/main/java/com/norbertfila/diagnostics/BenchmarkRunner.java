package com.norbertfila.diagnostics;

import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.meshing.ChunkMesher;
import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BenchmarkRunner {
    private final int warmupIterations;
    private final int measuredIterations;
    private final List<ChunkPos> positions;

    public BenchmarkRunner(int warmupIterations, int measuredIterations, List<ChunkPos> positions) {
        if (warmupIterations < 0 || measuredIterations <= 0) {
            throw new IllegalArgumentException("Warmup must be non-negative and measured iterations must be positive");
        }
        if (positions == null || positions.isEmpty()) throw new IllegalArgumentException("Positions must not be empty");
        this.warmupIterations = warmupIterations;
        this.measuredIterations = measuredIterations;
        this.positions = List.copyOf(positions);
    }

    public Result run(ScenePreset preset, ChunkMesher mesher, long seed) {
        Objects.requireNonNull(preset);
        Objects.requireNonNull(mesher);
        for (int i = 0; i < warmupIterations; i++) runIteration(preset, mesher, seed);
        long generateNanos = 0;
        long meshNanos = 0;
        long emittedFaces = 0;
        for (int i = 0; i < measuredIterations; i++) {
            long generateStart = System.nanoTime();
            List<ChunkSnapshot> snapshots = generate(preset, seed);
            generateNanos += System.nanoTime() - generateStart;
            long meshStart = System.nanoTime();
            for (int index = 0; index < snapshots.size(); index++) {
                ChunkPos position = positions.get(index);
                MeshData mesh = mesher.mesh(
                        snapshots.get(index),
                        BlockNeighborhood.centerOnly(snapshots.get(index)),
                        position.x(),
                        position.y(),
                        position.z());
                emittedFaces += mesh.faceCount();
            }
            meshNanos += System.nanoTime() - meshStart;
        }
        return new Result(
                preset,
                seed,
                preset.generatorVersion(),
                positions.size(),
                warmupIterations,
                measuredIterations,
                generateNanos,
                meshNanos,
                emittedFaces);
    }

    public static String toCsv(List<Result> results) {
        StringBuilder out = new StringBuilder(
                "preset,seed,generatorVersion,chunks,warmupIterations,measuredIterations,generateNanos,meshNanos,emittedFaces\n");
        for (Result result : results) {
            out.append(result.preset())
                    .append(',')
                    .append(result.seed())
                    .append(',')
                    .append(result.generatorVersion())
                    .append(',')
                    .append(result.chunks())
                    .append(',')
                    .append(result.warmupIterations())
                    .append(',')
                    .append(result.measuredIterations())
                    .append(',')
                    .append(result.generateNanos())
                    .append(',')
                    .append(result.meshNanos())
                    .append(',')
                    .append(result.emittedFaces())
                    .append('\n');
        }
        return out.toString();
    }

    public static String toJson(List<Result> results) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            if (i > 0) out.append(',');
            out.append('{')
                    .append("\"preset\":\"")
                    .append(result.preset())
                    .append("\",")
                    .append("\"seed\":")
                    .append(result.seed())
                    .append(',')
                    .append("\"generatorVersion\":")
                    .append(result.generatorVersion())
                    .append(',')
                    .append("\"chunks\":")
                    .append(result.chunks())
                    .append(',')
                    .append("\"warmupIterations\":")
                    .append(result.warmupIterations())
                    .append(',')
                    .append("\"measuredIterations\":")
                    .append(result.measuredIterations())
                    .append(',')
                    .append("\"generateNanos\":")
                    .append(result.generateNanos())
                    .append(',')
                    .append("\"meshNanos\":")
                    .append(result.meshNanos())
                    .append(',')
                    .append("\"emittedFaces\":")
                    .append(result.emittedFaces())
                    .append('}');
        }
        return out.append(']').toString();
    }

    private List<ChunkSnapshot> generate(ScenePreset preset, long seed) {
        List<ChunkSnapshot> snapshots = new ArrayList<>(positions.size());
        for (ChunkPos position : positions) {
            snapshots.add(snapshot(preset.generate(position, seed)));
        }
        return snapshots;
    }

    private void runIteration(ScenePreset preset, ChunkMesher mesher, long seed) {
        for (int index = 0; index < positions.size(); index++) {
            ChunkPos position = positions.get(index);
            ChunkSnapshot snapshot = snapshot(preset.generate(position, seed));
            mesher.mesh(snapshot, BlockNeighborhood.centerOnly(snapshot), position.x(), position.y(), position.z());
        }
    }

    private static ChunkSnapshot snapshot(com.norbertfila.generation.ChunkData data) {
        return new ChunkSnapshot(data.blocks(), 0);
    }

    public record Result(
            ScenePreset preset,
            long seed,
            int generatorVersion,
            int chunks,
            int warmupIterations,
            int measuredIterations,
            long generateNanos,
            long meshNanos,
            long emittedFaces) {}
}
