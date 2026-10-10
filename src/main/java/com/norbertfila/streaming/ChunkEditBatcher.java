package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChunkEditBatcher {
    private static final Comparator<Edit> ORDER = Comparator.comparingInt(
                    (Edit edit) -> edit.position().x())
            .thenComparingInt(edit -> edit.position().y())
            .thenComparingInt(edit -> edit.position().z());
    private final Map<ChunkPos, Long> pending = new HashMap<>();

    public void record(ChunkPos position, long revision) {
        Objects.requireNonNull(position);
        if (revision < 0) throw new IllegalArgumentException("Revision must not be negative");
        pending.merge(position, revision, Math::max);
    }

    public List<Edit> drain() {
        List<Edit> edits = new ArrayList<>(pending.size());
        pending.forEach((position, revision) -> edits.add(new Edit(position, revision)));
        edits.sort(ORDER);
        pending.clear();
        return List.copyOf(edits);
    }

    public int size() {
        return pending.size();
    }

    public record Edit(ChunkPos position, long revision) {}
}
