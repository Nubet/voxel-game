package com.norbertfila.render;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class UploadQueue {
    private final Map<ChunkPos, Request> requests = new LinkedHashMap<>();

    public void enqueue(ChunkPos position, MeshData mesh) {
        requests.put(Objects.requireNonNull(position), new Request(position, Objects.requireNonNull(mesh)));
    }

    public Request poll(long remainingBytes, boolean firstUpload) {
        if (remainingBytes < 0) throw new IllegalArgumentException("Remaining bytes must not be negative");
        Request next = requests.values().stream().findFirst().orElse(null);
        if (next == null) return null;
        if (firstUpload || next.bytes() <= remainingBytes) {
            requests.remove(next.position());
            return next;
        }
        return null;
    }

    public int size() {
        return requests.size();
    }

    public Request remove(ChunkPos position) {
        return requests.remove(Objects.requireNonNull(position));
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
