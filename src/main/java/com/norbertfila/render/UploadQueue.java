package com.norbertfila.render;

import com.norbertfila.streaming.ChunkManager.MeshResult;
import com.norbertfila.world.ChunkPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class UploadQueue {
    private final Map<ChunkPos, MeshResult> requests = new LinkedHashMap<>();

    public void enqueue(MeshResult result) {
        Objects.requireNonNull(result);
        requests.put(result.position(), result);
    }

    public MeshResult poll(long remainingBytes, boolean firstUpload, Predicate<MeshResult> isCurrent) {
        if (remainingBytes < 0) throw new IllegalArgumentException("Remaining bytes must not be negative");
        Objects.requireNonNull(isCurrent);
        var iterator = requests.values().iterator();
        while (iterator.hasNext()) {
            MeshResult next = iterator.next();
            if (!isCurrent.test(next)) {
                iterator.remove();
                continue;
            }
            if (firstUpload || next.bytes() <= remainingBytes) {
                iterator.remove();
                return next;
            }
            return null;
        }
        return null;
    }

    public int size() {
        return requests.size();
    }

    public MeshResult remove(ChunkPos position) {
        return requests.remove(Objects.requireNonNull(position));
    }
}
