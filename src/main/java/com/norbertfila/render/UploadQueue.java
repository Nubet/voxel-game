package com.norbertfila.render;

import com.norbertfila.streaming.ChunkManager.MeshResult;
import com.norbertfila.world.ChunkPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Pending mesh uploads, one per chunk, in arrival order. */
public final class UploadQueue {
    // How far past an oversized head the queue looks for a result that still fits the frame budget.
    private static final int LOOKAHEAD = 32;
    private final Map<ChunkPos, MeshResult> requests = new LinkedHashMap<>();

    public void enqueue(MeshResult result) {
        Objects.requireNonNull(result);
        requests.put(result.position(), result);
    }

    /**
     * Returns the oldest current result that fits {@code remainingBytes}, dropping stale results on the way. The first
     * upload of a frame may exceed the budget, so a large result at the head is never starved: smaller ones may pass
     * it within one frame, but it is taken first in the next.
     */
    public MeshResult poll(long remainingBytes, boolean firstUpload, Predicate<MeshResult> isCurrent) {
        if (remainingBytes < 0) throw new IllegalArgumentException("Remaining bytes must not be negative");
        Objects.requireNonNull(isCurrent);
        var iterator = requests.values().iterator();
        int examined = 0;
        while (iterator.hasNext() && examined < LOOKAHEAD) {
            MeshResult next = iterator.next();
            if (!isCurrent.test(next)) {
                iterator.remove();
                continue;
            }
            if (firstUpload || next.bytes() <= remainingBytes) {
                iterator.remove();
                return next;
            }
            examined++;
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
