package com.norbertfila.render;

import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class IndirectTerrainRenderer {
    public Result build(Collection<Batch> batches, Frustum frustum) {
        Objects.requireNonNull(batches);
        Objects.requireNonNull(frustum);
        long start = System.nanoTime();
        List<DrawCommand> commands = new ArrayList<>(batches.size());
        for (Batch batch : batches) {
            Objects.requireNonNull(batch);
            if (batch.vertexCount() > 0 && frustum.intersects(batch.position())) {
                commands.add(new DrawCommand(
                        batch.vertexCount(), 1, batch.firstVertex(), commands.size(), batch.position()));
            }
        }
        return new Result(commands, System.nanoTime() - start);
    }

    public record Batch(ChunkPos position, int firstVertex, int vertexCount) {
        public Batch {
            Objects.requireNonNull(position);
            if (firstVertex < 0 || vertexCount < 0) throw new IllegalArgumentException("Invalid draw range");
        }
    }

    public record DrawCommand(int count, int instanceCount, int first, int baseInstance, ChunkPos position) {
        public DrawCommand {
            Objects.requireNonNull(position);
            if (count <= 0 || instanceCount != 1 || first < 0 || baseInstance < 0) {
                throw new IllegalArgumentException("Invalid indirect draw command");
            }
        }
    }

    public record Result(List<DrawCommand> commands, long buildNanos) {
        public Result {
            commands = List.copyOf(commands);
        }

        public int drawCalls() {
            return commands.isEmpty() ? 0 : 1;
        }

        public int visibleMeshes() {
            return commands.size();
        }
    }
}
