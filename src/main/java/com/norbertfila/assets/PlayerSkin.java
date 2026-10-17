package com.norbertfila.assets;

import java.util.Objects;

public final class PlayerSkin {
    public static final int SIZE = 64;
    private final byte[] rgba;

    public PlayerSkin(byte[] rgba) {
        if (Objects.requireNonNull(rgba).length != SIZE * SIZE * 4)
            throw new IllegalArgumentException("Expected 64x64 RGBA skin");
        this.rgba = rgba.clone();
    }

    public byte[] rgba() {
        return rgba.clone();
    }
}
