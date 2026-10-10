package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.streaming.ChunkManager.MeshResult;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class UploadQueueTest {
    @Test
    void dropsStaleOversizedResultsBeforeCheckingTheByteBudget() {
        UploadQueue queue = new UploadQueue();
        MeshData large = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE * 2]);
        MeshResult stale = new MeshResult(new ChunkPos(0, 0, 0), large, 1, 2, 0, 3);
        MeshResult current = new MeshResult(new ChunkPos(1, 0, 0), new MeshData(new float[0]), 4, 5, 1, 6);
        queue.enqueue(stale);
        queue.enqueue(current);
        var discarded = new java.util.ArrayList<MeshResult>();

        MeshResult polled = queue.poll(0, false, result -> {
            if (result == current) return true;
            discarded.add(result);
            return false;
        });

        assertSame(current, polled);
        assertEquals(java.util.List.of(stale), discarded);
        assertEquals(0, queue.size());
        assertEquals(4, polled.dataRevision());
        assertEquals(5, polled.meshDependencyRevision());
        assertEquals(6, polled.token());
    }

    @Test
    void respectsByteBudgetAfterAllowingOneOversizedUpload() {
        UploadQueue queue = new UploadQueue();
        MeshData small = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE]);
        MeshData large = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE * 2]);
        queue.enqueue(result(new ChunkPos(0, 0, 0), large));
        queue.enqueue(result(new ChunkPos(1, 0, 0), small));

        MeshResult first = queue.poll(smallBytes(small), true, mesh -> true);
        assertEquals(large, first.mesh());
        assertNull(queue.poll(0, false, mesh -> true));
        assertEquals(1, queue.size());
    }

    @Test
    void preservesFifoOrderForSmallUploads() {
        UploadQueue queue = new UploadQueue();
        MeshData mesh = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE]);
        queue.enqueue(result(new ChunkPos(0, 0, 0), mesh));
        queue.enqueue(result(new ChunkPos(1, 0, 0), mesh));

        assertEquals(
                new ChunkPos(0, 0, 0),
                queue.poll(smallBytes(mesh), true, result -> true).position());
        assertEquals(
                new ChunkPos(1, 0, 0),
                queue.poll(smallBytes(mesh), false, result -> true).position());
        assertNull(queue.poll(smallBytes(mesh), false, result -> true));
    }

    @Test
    void replacesQueuedMeshForTheSameChunkAndCanRemoveIt() {
        UploadQueue queue = new UploadQueue();
        MeshData first = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE]);
        MeshData replacement = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE * 2]);
        ChunkPos position = new ChunkPos(3, 0, 4);

        queue.enqueue(result(position, first));
        queue.enqueue(result(position, replacement));

        assertEquals(1, queue.size());
        assertEquals(replacement, queue.poll(Long.MAX_VALUE, true, mesh -> true).mesh());
        queue.enqueue(result(position, first));
        assertEquals(position, queue.remove(position).position());
        assertEquals(0, queue.size());
    }

    private static long smallBytes(MeshData mesh) {
        return mesh.vertexCount() * (long) MeshData.VERTEX_STRIDE * Float.BYTES;
    }

    private static MeshResult result(ChunkPos position, MeshData mesh) {
        return new MeshResult(position, mesh, 0, 0, 0, 0);
    }
}
