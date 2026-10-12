package com.norbertfila.world;

import java.util.Objects;

public record BlockDefinition(
        int id,
        String name,
        int topTexture,
        int bottomTexture,
        int sideTexture,
        boolean collidable,
        boolean occludesFullFace,
        RenderLayer renderLayer) {
    public BlockDefinition {
        if (id < 0 || id > 255) throw new IllegalArgumentException("Block id must fit an unsigned byte");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Block name must not be blank");
        if (topTexture < 0 || bottomTexture < 0 || sideTexture < 0)
            throw new IllegalArgumentException("Texture layers must be non-negative");
        Objects.requireNonNull(renderLayer);
    }

    public int textureLayer(int face) {
        return switch (face) {
            case 2 -> topTexture;
            case 3 -> bottomTexture;
            case 0, 1, 4, 5 -> sideTexture;
            default -> throw new IllegalArgumentException("Face index must be in [0, 6)");
        };
    }
}
