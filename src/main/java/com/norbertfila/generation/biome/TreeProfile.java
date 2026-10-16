package com.norbertfila.generation.biome;

public record TreeProfile(float density, TreeType type) {
    public TreeProfile {
        if (!Float.isFinite(density) || density < 0 || density > 1)
            throw new IllegalArgumentException("Tree density must be finite and within [0, 1]");
        if (type == null) throw new NullPointerException("type");
    }
}
