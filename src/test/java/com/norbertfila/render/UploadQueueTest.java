package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class UploadQueueTest {
    @Test
    void respectsByteBudgetAfterAllowingOneOversizedUpload() {
        UploadQueue queue = new UploadQueue();
        MeshData small = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE]);
        MeshData large = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE * 2]);
        queue.enqueue(new ChunkPos(0, 0, 0), large);
        queue.enqueue(new ChunkPos(1, 0, 0), small);

        UploadQueue.Request first = queue.poll(smallBytes(small), true);
        assertEquals(large, first.mesh());
        assertNull(queue.poll(0, false));
        assertEquals(1, queue.size());
    }

    @Test
    void preservesFifoOrderForSmallUploads() {
        UploadQueue queue = new UploadQueue();
        MeshData mesh = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE]);
        queue.enqueue(new ChunkPos(0, 0, 0), mesh);
        queue.enqueue(new ChunkPos(1, 0, 0), mesh);

        assertEquals(new ChunkPos(0, 0, 0), queue.poll(smallBytes(mesh), true).position());
        assertEquals(new ChunkPos(1, 0, 0), queue.poll(smallBytes(mesh), false).position());
        assertNull(queue.poll(smallBytes(mesh), false));
    }

    @Test
    void replacesQueuedMeshForTheSameChunkAndCanRemoveIt() {
        UploadQueue queue = new UploadQueue();
        MeshData first = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE]);
        MeshData replacement = new MeshData(new float[MeshData.VERTEX_STRIDE * MeshData.VERTICES_PER_FACE * 2]);
        ChunkPos position = new ChunkPos(3, 0, 4);

        queue.enqueue(position, first);
        queue.enqueue(position, replacement);

        assertEquals(1, queue.size());
        assertEquals(replacement, queue.poll(Long.MAX_VALUE, true).mesh());
        queue.enqueue(position, first);
        assertEquals(position, queue.remove(position).position());
        assertEquals(0, queue.size());
    }

    private static long smallBytes(MeshData mesh) {
        return mesh.vertexCount() * (long) MeshData.VERTEX_STRIDE * Float.BYTES;
    }
}
