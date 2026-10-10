package com.norbertfila.diagnostics;

import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.meshing.ChunkMesher;
import com.norbertfila.meshing.GreedyMesher;
import com.norbertfila.meshing.MeshData;
import com.norbertfila.meshing.VisibleFaceMesher;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MesherComparison {
    private MesherComparison() {}

    public static List<Result> run(int warmupIterations, int measuredIterations, List<ChunkPos> positions, long seed) {
        if (warmupIterations < 0 || measuredIterations <= 0)
            throw new IllegalArgumentException("Invalid benchmark iterations");
        if (positions == null || positions.isEmpty()) throw new IllegalArgumentException("Positions must not be empty");
        List<Result> results = new ArrayList<>();
        for (ScenePreset preset : ScenePreset.values()) {
            List<Case> cases = cases(preset, positions, seed);
            Result result = compare(preset, seed, cases, warmupIterations, measuredIterations);
            results.add(result);
        }
        return List.copyOf(results);
    }

    public static String toCsv(List<Result> results) {
        StringBuilder out = new StringBuilder(
                "preset,seed,chunks,baselineNanos,greedyNanos,baselineVertices,greedyVertices,baselineBytes,greedyBytes,baselineSingleRemeshNanos,baselineBatchRemeshNanos,greedySingleRemeshNanos,greedyBatchRemeshNanos,equivalent\n");
        for (Result result : results) {
            out.append(result.preset())
                    .append(',')
                    .append(result.seed())
                    .append(',')
                    .append(result.chunks())
                    .append(',')
                    .append(result.baselineNanos())
                    .append(',')
                    .append(result.greedyNanos())
                    .append(',')
                    .append(result.baselineVertices())
                    .append(',')
                    .append(result.greedyVertices())
                    .append(',')
                    .append(result.baselineBytes())
                    .append(',')
                    .append(result.greedyBytes())
                    .append(',')
                    .append(result.baselineSingleRemeshNanos())
                    .append(',')
                    .append(result.baselineBatchRemeshNanos())
                    .append(',')
                    .append(result.greedySingleRemeshNanos())
                    .append(',')
                    .append(result.greedyBatchRemeshNanos())
                    .append(',')
                    .append(result.equivalent())
                    .append('\n');
        }
        return out.toString();
    }

    public static void main(String[] args) {
        System.out.print(toCsv(run(2, 5, List.of(new ChunkPos(0, 0, 0), new ChunkPos(1, 0, 0)), 0x5EEDL)));
    }

    private static Result compare(
            ScenePreset preset, long seed, List<Case> cases, int warmupIterations, int measuredIterations) {
        ChunkMesher baseline = new VisibleFaceMesher();
        ChunkMesher greedy = new GreedyMesher();
        for (int i = 0; i < warmupIterations; i++) {
            measure(cases, baseline, 1, false);
            measure(cases, greedy, 1, false);
        }
        Measurement baselineMeasurement = measure(cases, baseline, measuredIterations, true);
        Measurement greedyMeasurement = measure(cases, greedy, measuredIterations, true);
        long baselineSingleRemeshNanos = measure(List.of(cases.get(0)), baseline, measuredIterations, false)
                .nanos();
        long baselineBatchRemeshNanos =
                measure(cases, baseline, measuredIterations, false).nanos();
        long greedySingleRemeshNanos = measure(List.of(cases.get(0)), greedy, measuredIterations, false)
                .nanos();
        long greedyBatchRemeshNanos =
                measure(cases, greedy, measuredIterations, false).nanos();
        return new Result(
                preset,
                seed,
                cases.size(),
                baselineMeasurement.nanos(),
                greedyMeasurement.nanos(),
                baselineMeasurement.vertices(),
                greedyMeasurement.vertices(),
                (long) baselineMeasurement.vertices() * MeshData.VERTEX_STRIDE * Float.BYTES,
                (long) greedyMeasurement.vertices() * MeshData.VERTEX_STRIDE * Float.BYTES,
                baselineSingleRemeshNanos,
                baselineBatchRemeshNanos,
                greedySingleRemeshNanos,
                greedyBatchRemeshNanos,
                equivalent(cases, baseline, greedy));
    }

    private static Measurement measure(List<Case> cases, ChunkMesher mesher, int iterations, boolean collect) {
        long start = System.nanoTime();
        int vertices = 0;
        for (int iteration = 0; iteration < iterations; iteration++) {
            for (Case entry : cases) {
                MeshData mesh = mesher.mesh(
                        entry.snapshot(),
                        entry.neighborhood(),
                        entry.position().x(),
                        entry.position().y(),
                        entry.position().z());
                if (collect && iteration == iterations - 1) vertices += mesh.vertexCount();
            }
        }
        return new Measurement(System.nanoTime() - start, vertices);
    }

    private static boolean equivalent(List<Case> cases, ChunkMesher baseline, ChunkMesher greedy) {
        for (Case entry : cases) {
            MeshData expected = baseline.mesh(
                    entry.snapshot(),
                    entry.neighborhood(),
                    entry.position().x(),
                    entry.position().y(),
                    entry.position().z());
            MeshData actual = greedy.mesh(
                    entry.snapshot(),
                    entry.neighborhood(),
                    entry.position().x(),
                    entry.position().y(),
                    entry.position().z());
            long expectedArea = exposedArea(entry.snapshot(), entry.neighborhood());
            if (surfaceArea(expected) != surfaceArea(actual) || surfaceArea(expected) != expectedArea * 2) return false;
        }
        return true;
    }

    private static List<Case> cases(ScenePreset preset, List<ChunkPos> positions, long seed) {
        List<Case> cases = new ArrayList<>(positions.size());
        for (ChunkPos position : positions) {
            ChunkSnapshot snapshot =
                    new ChunkSnapshot(preset.generate(position, seed).blocks(), 0);
            cases.add(new Case(position, snapshot, BlockNeighborhood.centerOnly(snapshot)));
        }
        return cases;
    }

    private static long exposedArea(ChunkSnapshot snapshot, BlockNeighborhood neighborhood) {
        long area = 0;
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    if (snapshot.get(x, y, z) == BlockId.AIR) continue;
                    for (int face = 0; face < 6; face++) {
                        int nx = x + VisibleFaceMesher.NORMALS[face][0];
                        int ny = y + VisibleFaceMesher.NORMALS[face][1];
                        int nz = z + VisibleFaceMesher.NORMALS[face][2];
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < Chunk.SIZE && ny < Chunk.SIZE && nz < Chunk.SIZE) {
                            if (snapshot.get(nx, ny, nz) == BlockId.AIR) area++;
                        } else if (!solidNeighbor(neighborhood, face, x, y, z)) area++;
                    }
                }
        return area;
    }

    private static boolean solidNeighbor(BlockNeighborhood neighborhood, int face, int x, int y, int z) {
        ChunkSnapshot neighbor =
                neighborhood.neighbor(BlockNeighborhood.Side.values()[face]).orElse(null);
        if (neighbor == null) return false;
        return switch (face) {
            case 0 -> neighbor.get(0, y, z) != BlockId.AIR;
            case 1 -> neighbor.get(Chunk.SIZE - 1, y, z) != BlockId.AIR;
            case 2 -> neighbor.get(x, 0, z) != BlockId.AIR;
            case 3 -> neighbor.get(x, Chunk.SIZE - 1, z) != BlockId.AIR;
            case 4 -> neighbor.get(x, y, 0) != BlockId.AIR;
            case 5 -> neighbor.get(x, y, Chunk.SIZE - 1) != BlockId.AIR;
            default -> throw new AssertionError(face);
        };
    }

    private static long surfaceArea(MeshData mesh) {
        float[] vertices = mesh.vertices();
        long doubledArea = 0;
        for (int i = 0; i < vertices.length; i += MeshData.VERTEX_STRIDE * 3) {
            float ax = vertices[i + 6] - vertices[i],
                    ay = vertices[i + 7] - vertices[i + 1],
                    az = vertices[i + 8] - vertices[i + 2];
            float bx = vertices[i + 12] - vertices[i],
                    by = vertices[i + 13] - vertices[i + 1],
                    bz = vertices[i + 14] - vertices[i + 2];
            doubledArea += Math.round(Math.sqrt((ay * bz - az * by) * (ay * bz - az * by)
                    + (az * bx - ax * bz) * (az * bx - ax * bz)
                    + (ax * by - ay * bx) * (ax * by - ay * bx)));
        }
        return doubledArea;
    }

    private record Case(ChunkPos position, ChunkSnapshot snapshot, BlockNeighborhood neighborhood) {
        private Case {
            Objects.requireNonNull(position);
            Objects.requireNonNull(snapshot);
            Objects.requireNonNull(neighborhood);
        }
    }

    private record Measurement(long nanos, int vertices) {}

    public record Result(
            ScenePreset preset,
            long seed,
            int chunks,
            long baselineNanos,
            long greedyNanos,
            int baselineVertices,
            int greedyVertices,
            long baselineBytes,
            long greedyBytes,
            long baselineSingleRemeshNanos,
            long baselineBatchRemeshNanos,
            long greedySingleRemeshNanos,
            long greedyBatchRemeshNanos,
            boolean equivalent) {}
}
