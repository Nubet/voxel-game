package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.meshing.QuadRanges;
import com.norbertfila.streaming.ChunkManager.MeshResult;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class UploadQueueTest {
    @Test
    void accountsForAllLayersAndReplacesTheirRangesTogether() {
        QuadMesh mixed = solid(3, new QuadRanges(1, 1, 1));
        MeshResult request = result(new ChunkPos(-1, 0, 0), mixed);
        long expectedBytes = 3L * QuadMesh.Format.SOLID.ints() * Integer.BYTES;
        assertEquals(expectedBytes, request.bytes());
        UploadQueue queue = new UploadQueue();
        queue.enqueue(request);
        assertNull(queue.poll(expectedBytes - 1, false, current -> true));
        assertSame(request, queue.poll(expectedBytes, false, current -> true));

        queue.enqueue(request);
        QuadMesh dry = solid(1, new QuadRanges(1, 0, 0));
        MeshResult replacement = new MeshResult(request.position(), new ChunkMesh(dry, null), 2, 3, 4, 5);
        queue.enqueue(replacement);
        assertEquals(1, queue.size());
        assertSame(replacement, queue.poll(expectedBytes, false, current -> true));
        assertEquals(new QuadRanges(1, 0, 0), replacement.mesh().solid().ranges());
        assertEquals(5, replacement.token());
    }

    @Test
    void dropsStaleOversizedResultsBeforeCheckingTheByteBudget() {
        UploadQueue queue = new UploadQueue();
        QuadMesh large = solid(2);
        MeshResult stale = new MeshResult(new ChunkPos(0, 0, 0), new ChunkMesh(large, null), 1, 2, 0, 3);
        MeshResult current = new MeshResult(new ChunkPos(1, 0, 0), new ChunkMesh(solid(0), null), 4, 5, 1, 6);
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
        QuadMesh small = solid(1);
        QuadMesh large = solid(2);
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
        QuadMesh mesh = solid(1);
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
        QuadMesh first = solid(1);
        QuadMesh replacement = solid(2);
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

    private static long smallBytes(QuadMesh mesh) {
        return mesh.bytes();
    }

    private static QuadMesh solid(int quads) {
        return solid(quads, new QuadRanges(quads, 0, 0));
    }

    private static QuadMesh solid(int quads, QuadRanges ranges) {
        return new QuadMesh(QuadMesh.Format.SOLID, new int[quads * QuadMesh.Format.SOLID.ints()], ranges);
    }

    private static MeshResult result(ChunkPos position, QuadMesh mesh) {
        return new MeshResult(position, new ChunkMesh(mesh, null), 0, 0, 0, 0);
    }
}
