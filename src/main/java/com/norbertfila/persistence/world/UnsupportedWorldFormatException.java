package com.norbertfila.persistence.world;

import java.io.IOException;

public final class UnsupportedWorldFormatException extends IOException {
    public UnsupportedWorldFormatException(String message) {
        super(message);
    }
}
