package com.norbertfila.render;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayDeque;
import java.util.Objects;

public final class UploadQueue {
    private final ArrayDeque<Request> requests = new ArrayDeque<>();

    public void enqueue(ChunkPos position, MeshData mesh) {
        requests.addLast(new Request(Objects.requireNonNull(position), Objects.requireNonNull(mesh)));
    }

    public Request poll(long remainingBytes, boolean firstUpload) {
        if (remainingBytes < 0) throw new IllegalArgumentException("Remaining bytes must not be negative");
        Request next = requests.peekFirst();
        if (next == null) return null;
        if (firstUpload || next.bytes() <= remainingBytes) return requests.removeFirst();
        return null;
    }

    public int size() {
        return requests.size();
    }

    public record Request(ChunkPos position, MeshData mesh) {
        public Request {
            Objects.requireNonNull(position);
            Objects.requireNonNull(mesh);
        }

        public long bytes() {
            return (long) mesh.vertexCount() * mesh.format().stride() * Float.BYTES;
        }
    }
}
