package com.norbertfila.player.visual.model;

// Top-left pixel origin in the canonical 64x64 skin layout, independent of face UV generation.
public record UvOrigin(int u, int v) {
    public UvOrigin {
        if (u < 0 || v < 0) throw new IllegalArgumentException("UV origin must be nonnegative");
    }
}
