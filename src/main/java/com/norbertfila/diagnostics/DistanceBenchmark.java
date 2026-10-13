package com.norbertfila.diagnostics;

import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.lod.FarTerrainTile;
import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.meshing.GreedyMesher;
import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkSnapshot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DistanceBenchmark {
    private static final int[] DISTANCES = {16, 32, 64, 128};
    private static final List<ChunkPos> ROUTE =
            List.of(new ChunkPos(0, 0, 0), new ChunkPos(4, 0, 4), new ChunkPos(-4, 0, 2));
    private static final int NEAR_RADIUS_CHUNKS = 8;
    private static final int FAR_TILE_SIZE = 64;
    private static final int FAR_SAMPLE_STEP = 8;
    private static final String ROUTE_ID = "fixed-route-v1";

    private DistanceBenchmark() {}

    public static List<Result> run(int warmupFrames, int measuredFrames, int runs, long seed) {
        if (warmupFrames < 0 || measuredFrames <= 0 || runs <= 0)
            throw new IllegalArgumentException("Invalid benchmark configuration");
        List<Result> results = new ArrayList<>();
        String commitHash = commitHash();
        for (int distance : DISTANCES)
            for (int run = 1; run <= runs; run++)
                results.add(run(distance, run, warmupFrames, measuredFrames, seed, commitHash));
        return List.copyOf(results);
    }

    public static String toCsv(List<Result> results) {
        StringBuilder out = new StringBuilder(
                "distanceChunks,run,seed,routeId,frames,averageFps,p99FrameMillis,generationNanos,meshingNanos,vramBytes,ramBytes,nearChunks,farTiles,streamingSpikeMillis,commitHash,gpuMeasured\n");
        for (Result result : results) {
            out.append(result.distanceChunks())
                    .append(',')
                    .append(result.run())
                    .append(',')
                    .append(result.seed())
                    .append(',')
                    .append(result.routeId())
                    .append(',')
                    .append(result.frames())
                    .append(',')
                    .append(result.averageFps())
                    .append(',')
                    .append(result.p99FrameMillis())
                    .append(',')
                    .append(result.generationNanos())
                    .append(',')
                    .append(result.meshingNanos())
                    .append(',')
                    .append(result.vramBytes())
                    .append(',')
                    .append(result.ramBytes())
                    .append(',')
                    .append(result.nearChunks())
                    .append(',')
                    .append(result.farTiles())
                    .append(',')
                    .append(result.streamingSpikeMillis())
                    .append(',')
                    .append(result.commitHash())
                    .append(',')
                    .append(result.gpuMeasured())
                    .append('\n');
        }
        return out.toString();
    }

    public static String toJson(List<Result> results) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) out.append(',');
            Result result = results.get(i);
            out.append('{')
                    .append("\"distanceChunks\":")
                    .append(result.distanceChunks())
                    .append(',')
                    .append("\"run\":")
                    .append(result.run())
                    .append(',')
                    .append("\"seed\":")
                    .append(result.seed())
                    .append(',')
                    .append("\"routeId\":\"")
                    .append(result.routeId())
                    .append("\",")
                    .append("\"frames\":")
                    .append(result.frames())
                    .append(',')
                    .append("\"averageFps\":")
                    .append(result.averageFps())
                    .append(',')
                    .append("\"p99FrameMillis\":")
                    .append(result.p99FrameMillis())
                    .append(',')
                    .append("\"generationNanos\":")
                    .append(result.generationNanos())
                    .append(',')
                    .append("\"meshingNanos\":")
                    .append(result.meshingNanos())
                    .append(',')
                    .append("\"vramBytes\":")
                    .append(result.vramBytes())
                    .append(',')
                    .append("\"ramBytes\":")
                    .append(result.ramBytes())
                    .append(',')
                    .append("\"nearChunks\":")
                    .append(result.nearChunks())
                    .append(',')
                    .append("\"farTiles\":")
                    .append(result.farTiles())
                    .append(',')
                    .append("\"streamingSpikeMillis\":")
                    .append(result.streamingSpikeMillis())
                    .append(',')
                    .append("\"commitHash\":\"")
                    .append(result.commitHash())
                    .append("\",")
                    .append("\"gpuMeasured\":")
                    .append(result.gpuMeasured())
                    .append('}');
        }
        return out.append(']').toString();
    }

    public static void main(String[] args) {
        List<Result> results = run(1, 2, 3, 0x5EEDL);
        System.out.print(toJson(results));
    }

    private static Result run(
            int distance, int run, int warmupFrames, int measuredFrames, long seed, String commitHash) {
        TerrainSource source = new HillsGenerator(seed);
        FarTerrainGenerator far =
                new FarTerrainGenerator(source, FAR_TILE_SIZE, FAR_SAMPLE_STEP, NEAR_RADIUS_CHUNKS, distance);
        GreedyMesher mesher = new GreedyMesher();
        for (int frame = 0; frame < warmupFrames; frame++) executeFrame(distance, frame, source, far, mesher, false);
        List<Double> frameMillis = new ArrayList<>(measuredFrames);
        long generationNanos = 0;
        long meshingNanos = 0;
        long vramBytes = 0;
        long ramBytes = 0;
        int nearChunks = 0;
        int farTiles = 0;
        for (int frame = 0; frame < measuredFrames; frame++) {
            Measurement measurement = executeFrame(distance, frame, source, far, mesher, true);
            frameMillis.add(measurement.elapsedNanos() / 1_000_000.0);
            generationNanos += measurement.generationNanos();
            meshingNanos += measurement.meshingNanos();
            vramBytes = measurement.vramBytes();
            ramBytes = measurement.ramBytes();
            nearChunks = measurement.nearChunks();
            farTiles = measurement.farTiles();
        }
        double[] sorted = frameMillis.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        double p99 = sorted[Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(sorted.length * .99) - 1))];
        double averageFps = 1000.0
                / (frameMillis.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0));
        double average =
                frameMillis.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new Result(
                distance,
                run,
                seed,
                ROUTE_ID,
                measuredFrames,
                averageFps,
                p99,
                generationNanos,
                meshingNanos,
                vramBytes,
                ramBytes,
                nearChunks,
                farTiles,
                p99 - average,
                commitHash,
                false);
    }

    private static Measurement executeFrame(
            int distance,
            int frame,
            TerrainSource source,
            FarTerrainGenerator far,
            GreedyMesher mesher,
            boolean measure) {
        ChunkPos camera = ROUTE.get(frame % ROUTE.size());
        long start = System.nanoTime();
        long generationStart = System.nanoTime();
        List<ChunkSnapshot> near = new ArrayList<>();
        int radius = Math.min(distance, NEAR_RADIUS_CHUNKS);
        for (int z = -radius; z <= radius; z++)
            for (int x = -radius; x <= radius; x++) {
                ChunkDataHolder data =
                        new ChunkDataHolder(source.generate(new ChunkPos(camera.x() + x, 0, camera.z() + z)));
                near.add(data.snapshot());
            }
        int farRadius = distance / (FAR_TILE_SIZE / Chunk.SIZE) + 1;
        long farRam = 0;
        long farVram = 0;
        int farCount = 0;
        for (int z = -farRadius; z <= farRadius; z++)
            for (int x = -farRadius; x <= farRadius; x++) {
                int tileX = Math.floorDiv(camera.x() * Chunk.SIZE, FAR_TILE_SIZE) + x;
                int tileZ = Math.floorDiv(camera.z() * Chunk.SIZE, FAR_TILE_SIZE) + z;
                if (far.inRange(camera, tileX, tileZ)) {
                    FarTerrainTile tile = far.generate(tileX, tileZ);
                    farRam += tile.estimatedBytes();
                    farVram += (long) tile.vertexCount() * MeshData.VERTEX_STRIDE * Float.BYTES;
                    farCount++;
                }
            }
        long generationNanos = System.nanoTime() - generationStart;
        long meshingStart = System.nanoTime();
        long nearVram = 0;
        for (ChunkSnapshot snapshot : near)
            nearVram += (long) mesher.mesh(
                                    snapshot,
                                    BlockNeighborhood.centerOnly(snapshot),
                                    camera.x(),
                                    camera.y(),
                                    camera.z())
                            .vertexCount()
                    * MeshData.VERTEX_STRIDE
                    * Float.BYTES;
        long meshingNanos = System.nanoTime() - meshingStart;
        long elapsedNanos = System.nanoTime() - start;
        return new Measurement(
                measure ? elapsedNanos : 0,
                generationNanos,
                meshingNanos,
                nearVram + farVram,
                near.size() * (long) Chunk.BLOCK_BYTES + farRam,
                near.size(),
                farCount);
    }

    private static String commitHash() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            String hash = new String(process.getInputStream().readAllBytes()).trim();
            return process.waitFor() == 0 && !hash.isEmpty() ? hash : "unknown";
        } catch (IOException failure) {
            return "unknown";
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private record Measurement(
            long elapsedNanos,
            long generationNanos,
            long meshingNanos,
            long vramBytes,
            long ramBytes,
            int nearChunks,
            int farTiles) {}

    private record ChunkDataHolder(com.norbertfila.generation.ChunkData data) {
        ChunkSnapshot snapshot() {
            return new ChunkSnapshot(data.blocks(), 0);
        }
    }

    public record Result(
            int distanceChunks,
            int run,
            long seed,
            String routeId,
            int frames,
            double averageFps,
            double p99FrameMillis,
            long generationNanos,
            long meshingNanos,
            long vramBytes,
            long ramBytes,
            int nearChunks,
            int farTiles,
            double streamingSpikeMillis,
            String commitHash,
            boolean gpuMeasured) {}
}
