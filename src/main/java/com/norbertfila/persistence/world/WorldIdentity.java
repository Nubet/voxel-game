package com.norbertfila.persistence.world;

import java.util.Objects;
import java.util.UUID;

public record WorldIdentity(UUID id, long seed, String generatorId, int generatorVersion) {
    public WorldIdentity {
        Objects.requireNonNull(id);
        Objects.requireNonNull(generatorId);
        if (!generatorId.matches("[a-z][a-z0-9_-]{0,63}") || generatorVersion < 1)
            throw new IllegalArgumentException("Invalid world generator identity");
    }
}
