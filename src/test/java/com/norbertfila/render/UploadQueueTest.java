package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.MeshData;
import com.norbertfila.meshing.MeshFormat;
import com.norbertfila.meshing.MeshRanges;
import com.norbertfila.streaming.ChunkManager.MeshResult;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class UploadQueueTest {
    @Test
    void accountsForAllLayersAndReplacesTheirRangesTogether() {
        MeshData mixed = MeshData.textured(new float[18 * MeshFormat.TEXTURED_VERTEX_STRIDE], new MeshRanges(6, 6, 6));
        MeshResult request = result(new ChunkPos(-1, 0, 0), mixed);
        long expectedBytes = 18L * MeshFormat.TEXTURED_VERTEX_STRIDE * Float.BYTES;
        assertEquals(expectedBytes, request.bytes());
        UploadQueue queue = new UploadQueue();
        queue.enqueue(request);
        assertNull(queue.poll(expectedBytes - 1, false, current -> true));
        assertSame(request, queue.poll(expectedBytes, false, current -> true));

        queue.enqueue(request);
        MeshData dry = MeshData.textured(new float[6 * MeshFormat.TEXTURED_VERTEX_STRIDE], new MeshRanges(6, 0, 0));
        MeshResult replacement = new MeshResult(request.position(), ChunkMesh.single(dry), 2, 3, 4, 5);
        queue.enqueue(replacement);
        assertEquals(1, queue.size());
        assertSame(replacement, queue.poll(expectedBytes, false, current -> true));
        assertEquals(new MeshRanges(6, 0, 0), replacement.mesh().solid().ranges());
        assertEquals(5, replacement.token());
    }

    @Test
    void dropsStaleOversizedResultsBeforeCheckingTheByteBudget() {
        UploadQueue queue = new UploadQueue();
        MeshData large = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE * 2]);
        MeshResult stale = new MeshResult(new ChunkPos(0, 0, 0), ChunkMesh.single(large), 1, 2, 0, 3);
        MeshResult current =
                new MeshResult(new ChunkPos(1, 0, 0), ChunkMesh.single(new MeshData(new float[0])), 4, 5, 1, 6);
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
        assertEquals(large, first.mesh().solid());
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
        assertEquals(
                replacement,
                queue.poll(Long.MAX_VALUE, true, mesh -> true).mesh().solid());
        queue.enqueue(result(position, first));
        assertEquals(position, queue.remove(position).position());
        assertEquals(0, queue.size());
    }

    private static long smallBytes(MeshData mesh) {
        return mesh.vertexCount() * (long) MeshData.VERTEX_STRIDE * Float.BYTES;
    }

    private static MeshResult result(ChunkPos position, MeshData mesh) {
        return new MeshResult(position, ChunkMesh.single(mesh), 0, 0, 0, 0);
    }
}
