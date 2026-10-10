package com.norbertfila.diagnostics;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkShape;
import java.util.ArrayList;
import java.util.List;

public final class ChunkSizeBenchmark {
    private ChunkSizeBenchmark() {}

    public static void main(String[] args) {
        System.out.print(toCsv(run(64, 2, 5, 0x5EEDL)));
    }

    public static List<Result> run(int volumeEdge, int warmupIterations, int measuredIterations, long seed) {
        if (warmupIterations < 0 || measuredIterations <= 0)
            throw new IllegalArgumentException("Invalid benchmark iterations");
        List<Result> results = new ArrayList<>();
        for (ChunkShape shape : List.of(ChunkShape.SIZE_16, ChunkShape.SIZE_32)) {
            for (Scene scene : Scene.values())
                results.add(run(shape, scene, volumeEdge, warmupIterations, measuredIterations, seed));
        }
        return List.copyOf(results);
    }

    public static String toCsv(List<Result> results) {
        StringBuilder out =
                new StringBuilder("shape,scene,volumeBlocks,chunkCount,meshingNanos,staleRemeshNanos,vramBytes\n");
        for (Result result : results) {
            out.append(result.shape().edge())
                    .append(',')
                    .append(result.scene())
                    .append(',')
                    .append(result.volumeBlocks())
                    .append(',')
                    .append(result.chunkCount())
                    .append(',')
                    .append(result.meshingNanos())
                    .append(',')
                    .append(result.staleRemeshNanos())
                    .append(',')
                    .append(result.vramBytes())
                    .append('\n');
        }
        return out.toString();
    }

    private static Result run(
            ChunkShape shape, Scene scene, int volumeEdge, int warmupIterations, int measuredIterations, long seed) {
        List<byte[]> chunks = chunks(shape, scene, volumeEdge, seed);
        for (int i = 0; i < warmupIterations; i++) mesh(chunks, shape.edge());
        long meshingNanos = elapsedNanos(chunks, shape.edge(), measuredIterations);
        byte[] edited = chunks.get(0).clone();
        edited[0] = edited[0] == BlockId.AIR ? BlockId.STONE : BlockId.AIR;
        long staleRemeshNanos = elapsedNanos(List.of(edited), shape.edge(), measuredIterations);
        long faces = worldFaces(scene, volumeEdge, seed);
        return new Result(
                shape,
                scene,
                (long) volumeEdge * volumeEdge * volumeEdge,
                shape.chunksForVolume(volumeEdge),
                meshingNanos,
                staleRemeshNanos,
                faces * MeshData.VERTICES_PER_FACE * MeshData.VERTEX_STRIDE * Float.BYTES);
    }

    private static List<byte[]> chunks(ChunkShape shape, Scene scene, int volumeEdge, long seed) {
        int count = Math.toIntExact(shape.chunksForVolume(volumeEdge));
        List<byte[]> chunks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            byte[] blocks = new byte[Math.toIntExact(shape.blockCount())];
            for (int i = 0; i < blocks.length; i++) blocks[i] = scene.block(i, blocks.length, seed + index);
            chunks.add(blocks);
        }
        return chunks;
    }

    private static long elapsedNanos(List<byte[]> chunks, int edge, int iterations) {
        long start = System.nanoTime();
        long faces = 0;
        for (int iteration = 0; iteration < iterations; iteration++) {
            for (byte[] chunk : chunks) faces += exposedFaces(chunk, edge);
        }
        if (faces < 0) throw new AssertionError("Face count overflow");
        return System.nanoTime() - start;
    }

    private static long mesh(List<byte[]> chunks, int edge) {
        long faces = 0;
        for (byte[] chunk : chunks) faces += exposedFaces(chunk, edge);
        return faces;
    }

    private static long exposedFaces(byte[] blocks, int edge) {
        long faces = 0;
        for (int y = 0; y < edge; y++)
            for (int z = 0; z < edge; z++)
                for (int x = 0; x < edge; x++) {
                    if (blocks[(y * edge + z) * edge + x] == BlockId.AIR) continue;
                    if (x == 0 || blocks[(y * edge + z) * edge + x - 1] == BlockId.AIR) faces++;
                    if (x == edge - 1 || blocks[(y * edge + z) * edge + x + 1] == BlockId.AIR) faces++;
                    if (z == 0 || blocks[(y * edge + z - 1) * edge + x] == BlockId.AIR) faces++;
                    if (z == edge - 1 || blocks[(y * edge + z + 1) * edge + x] == BlockId.AIR) faces++;
                    if (y == 0 || blocks[((y - 1) * edge + z) * edge + x] == BlockId.AIR) faces++;
                    if (y == edge - 1 || blocks[((y + 1) * edge + z) * edge + x] == BlockId.AIR) faces++;
                }
        return faces;
    }

    private static long worldFaces(Scene scene, int edge, long seed) {
        long faces = 0;
        for (int y = 0; y < edge; y++)
            for (int z = 0; z < edge; z++)
                for (int x = 0; x < edge; x++) {
                    if (scene.worldBlock(x, y, z, edge, seed) == BlockId.AIR) continue;
                    if (x == 0 || scene.worldBlock(x - 1, y, z, edge, seed) == BlockId.AIR) faces++;
                    if (x == edge - 1 || scene.worldBlock(x + 1, y, z, edge, seed) == BlockId.AIR) faces++;
                    if (z == 0 || scene.worldBlock(x, y, z - 1, edge, seed) == BlockId.AIR) faces++;
                    if (z == edge - 1 || scene.worldBlock(x, y, z + 1, edge, seed) == BlockId.AIR) faces++;
                    if (y == 0 || scene.worldBlock(x, y - 1, z, edge, seed) == BlockId.AIR) faces++;
                    if (y == edge - 1 || scene.worldBlock(x, y + 1, z, edge, seed) == BlockId.AIR) faces++;
                }
        return faces;
    }

    public enum Scene {
        MIXED,
        SOLID,
        HILLS,
        EDITED;

        private byte block(int index, int blockCount, long seed) {
            return switch (this) {
                case SOLID -> BlockId.STONE;
                case HILLS -> index / (blockCount / 16) < 8 ? BlockId.GRASS : BlockId.AIR;
                case EDITED -> (index + seed) % 11 == 0 ? BlockId.DIRT : BlockId.AIR;
                case MIXED -> (index * 31 + seed) % 7 == 0 ? BlockId.STONE : BlockId.AIR;
            };
        }

        private byte worldBlock(int x, int y, int z, int edge, long seed) {
            return block((y * edge + z) * edge + x, edge * edge * edge, seed);
        }
    }

    public record Result(
            ChunkShape shape,
            Scene scene,
            long volumeBlocks,
            long chunkCount,
            long meshingNanos,
            long staleRemeshNanos,
            long vramBytes) {}
}
