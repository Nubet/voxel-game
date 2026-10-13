package com.norbertfila.diagnostics;

import com.norbertfila.render.Frustum;
import com.norbertfila.render.IndirectTerrainRenderer;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

public final class IndirectTerrainBenchmark {
    private static final int[] BATCH_COUNTS = {1_000, 5_000, 20_000};
    private static final Frustum FRUSTUM =
            Frustum.from(new Matrix4f().ortho(-1_000_000, 1_000_000, -1_000, 1_000, -1_000, 1_000));

    private IndirectTerrainBenchmark() {}

    public static List<Result> run() {
        List<Result> results = new ArrayList<>(BATCH_COUNTS.length);
        for (int count : BATCH_COUNTS) results.add(run(count));
        return results;
    }

    private static Result run(int count) {
        List<IndirectTerrainRenderer.Batch> batches = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            batches.add(new IndirectTerrainRenderer.Batch(new ChunkPos(index, 0, 0), index * 36, 36));
        }
        long baselineStart = System.nanoTime();
        int baselineDrawCalls = 0;
        for (IndirectTerrainRenderer.Batch batch : batches) {
            if (FRUSTUM.intersects(batch.position()) && batch.vertexCount() > 0) baselineDrawCalls++;
        }
        long baselineNanos = System.nanoTime() - baselineStart;
        IndirectTerrainRenderer.Result indirect = new IndirectTerrainRenderer().build(batches, FRUSTUM);
        return new Result(count, baselineDrawCalls, indirect.drawCalls(), baselineNanos, indirect.buildNanos(), false);
    }

    public static void main(String[] args) {
        List<Result> results = run();
        StringBuilder output = new StringBuilder("[");
        for (int index = 0; index < results.size(); index++) {
            if (index > 0) output.append(',');
            Result result = results.get(index);
            output.append(result.toJson());
        }
        System.out.print(output.append(']'));
    }

    public record Result(
            int batchCount,
            int baselineDrawCalls,
            int indirectDrawCalls,
            long baselineCpuNanos,
            long indirectCpuNanos,
            boolean gpuMeasured) {
        private String toJson() {
            return "{\"batchCount\":"
                    + batchCount
                    + ",\"baselineDrawCalls\":"
                    + baselineDrawCalls
                    + ",\"indirectDrawCalls\":"
                    + indirectDrawCalls
                    + ",\"baselineCpuNanos\":"
                    + baselineCpuNanos
                    + ",\"indirectCpuNanos\":"
                    + indirectCpuNanos
                    + ",\"gpuMeasured\":"
                    + gpuMeasured
                    + "}";
        }
    }
}
