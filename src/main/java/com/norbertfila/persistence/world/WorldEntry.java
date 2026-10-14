package com.norbertfila.persistence.world;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WorldEntry(
        UUID id,
        Optional<WorldMetadata> metadata,
        Optional<WorldStateCodec.Header> header,
        Status status,
        String problem) {
    public enum Status {
        READY,
        INCOMPLETE,
        CORRUPTED,
        UNSUPPORTED_VERSION,
        UNREADABLE
    }

    public WorldEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(header);
        Objects.requireNonNull(status);
        Objects.requireNonNull(problem);
    }

    public String name() {
        return metadata.map(WorldMetadata::name).orElseGet(id::toString);
    }

    public Instant lastSavedAt() {
        return header.map(WorldStateCodec.Header::lastSavedAt).orElse(Instant.EPOCH);
    }
}
