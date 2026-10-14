package com.norbertfila.ui;

import com.norbertfila.persistence.world.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

public final class WorldUiFixtures {
    private WorldUiFixtures() {}

    public static WorldEntry entry(int id, long savedAt, WorldEntry.Status status) {
        var identity = new WorldIdentity(new UUID(0, id), 0, "default", 11);
        var metadata = new WorldMetadata(identity, "Zażółć gęślą jaźń " + id, Instant.EPOCH);
        return new WorldEntry(
                identity.id(),
                Optional.of(metadata),
                Optional.of(new WorldStateCodec.Header(identity, 0, Instant.ofEpochSecond(savedAt), 92)),
                status,
                "");
    }

    public static List<WorldEntry> entries(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> entry(id, id / 2, id % 7 == 0 ? WorldEntry.Status.CORRUPTED : WorldEntry.Status.READY))
                .toList();
    }
}
