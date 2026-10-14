package com.norbertfila.ui;

import java.util.Objects;

public sealed interface UiEvent {
    record Key(UiKey key) implements UiEvent {
        public Key {
            Objects.requireNonNull(key);
        }
    }

    record Character(int codePoint) implements UiEvent {
        public Character {
            if (!java.lang.Character.isValidCodePoint(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF)
                throw new IllegalArgumentException("Invalid Unicode scalar");
        }
    }
}
