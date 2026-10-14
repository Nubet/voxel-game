package com.norbertfila.persistence.world;

import java.time.Instant;
import java.util.Objects;

public record WorldMetadata(WorldIdentity identity, String name, Instant createdAt) {
    public WorldMetadata {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(createdAt);
        name = normalizeName(name);
        if (createdAt.isBefore(Instant.EPOCH)) throw new IllegalArgumentException("Invalid world creation time");
        createdAt = Instant.ofEpochMilli(createdAt.toEpochMilli());
    }

    public static String normalizeName(String name) {
        Objects.requireNonNull(name);
        name = name.strip();
        if (name.isEmpty()
                || name.codePointCount(0, name.length()) > 64
                || name.codePoints()
                        .anyMatch(c -> Character.isISOControl(c)
                                || Character.getType(c) == Character.LINE_SEPARATOR
                                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR
                                || c >= 0xD800 && c <= 0xDFFF))
            throw new IllegalArgumentException("World name must contain 1..64 printable Unicode code points");
        return name;
    }
}
