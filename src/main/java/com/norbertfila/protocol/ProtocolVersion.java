package com.norbertfila.protocol;

public final class ProtocolVersion {
    public static final int CURRENT = 2;

    private ProtocolVersion() {}

    public static boolean isSupported(int version) {
        return version == CURRENT;
    }
}
