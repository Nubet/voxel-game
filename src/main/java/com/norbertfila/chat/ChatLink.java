package com.norbertfila.chat;

import java.nio.file.Path;
import java.util.Objects;

public record ChatLink(int start, int end, Action action) {
    public ChatLink {
        if (start < 0 || end <= start) throw new IllegalArgumentException("Invalid chat link range");
        Objects.requireNonNull(action);
    }

    public sealed interface Action permits OpenFile, SuggestCommand {}

    public record SuggestCommand(String command) implements Action {
        public SuggestCommand {
            Objects.requireNonNull(command);
            if (!command.startsWith("/")
                    || command.codePointCount(0, command.length()) > ChatHistory.MAX_INPUT
                    || command.codePoints()
                            .anyMatch(cp -> Character.isISOControl(cp)
                                    || cp >= 0xD800 && cp <= 0xDFFF
                                    || Character.getType(cp) == Character.LINE_SEPARATOR
                                    || Character.getType(cp) == Character.PARAGRAPH_SEPARATOR))
                throw new IllegalArgumentException("Invalid suggested command");
        }
    }

    public record OpenFile(Path path) implements Action {
        public OpenFile {
            path = Objects.requireNonNull(path).toAbsolutePath().normalize();
        }
    }
}
