package com.norbertfila.chat;

import java.nio.file.Path;
import java.util.Objects;

public record ChatLink(int start, int end, Action action) {
    public ChatLink {
        if (start < 0 || end <= start) throw new IllegalArgumentException("Invalid chat link range");
        Objects.requireNonNull(action);
    }

    public sealed interface Action permits OpenFile {}

    public record OpenFile(Path path) implements Action {
        public OpenFile {
            path = Objects.requireNonNull(path).toAbsolutePath().normalize();
        }
    }
}
