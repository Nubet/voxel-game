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
        RenderLayer renderLayer,
        boolean breakable,
        boolean targetable,
        BlockShape shape,
        boolean replaceable,
        double breakSeconds) {
    public BlockDefinition(
            int id,
            String name,
            int topTexture,
            int bottomTexture,
            int sideTexture,
            boolean collidable,
            boolean occludesFullFace,
            RenderLayer renderLayer,
            boolean breakable,
            boolean targetable,
            BlockShape shape,
            boolean replaceable) {
        this(
                id,
                name,
                topTexture,
                bottomTexture,
                sideTexture,
                collidable,
                occludesFullFace,
                renderLayer,
                breakable,
                targetable,
                shape,
                replaceable,
                1);
    }

    public BlockDefinition(
            int id,
            String name,
            int topTexture,
            int bottomTexture,
            int sideTexture,
            boolean collidable,
            boolean occludesFullFace,
            RenderLayer renderLayer,
            boolean breakable,
            boolean targetable) {
        this(
                id,
                name,
                topTexture,
                bottomTexture,
                sideTexture,
                collidable,
                occludesFullFace,
                renderLayer,
                breakable,
                targetable,
                BlockShape.CUBE,
                id == BlockId.AIR || id == BlockId.WATER);
    }

    public BlockDefinition(
            int id,
            String name,
            int topTexture,
            int bottomTexture,
            int sideTexture,
            boolean collidable,
            boolean occludesFullFace,
            RenderLayer renderLayer) {
        this(
                id,
                name,
                topTexture,
                bottomTexture,
                sideTexture,
                collidable,
                occludesFullFace,
                renderLayer,
                id != BlockId.AIR,
                id != BlockId.AIR);
    }

    public BlockDefinition {
        if (id < 0 || id > 255) throw new IllegalArgumentException("Block id must fit an unsigned byte");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Block name must not be blank");
        if (topTexture < 0 || bottomTexture < 0 || sideTexture < 0)
            throw new IllegalArgumentException("Texture layers must be non-negative");
        Objects.requireNonNull(renderLayer);
        Objects.requireNonNull(shape);
        if (!Double.isFinite(breakSeconds) || breakSeconds < 0)
            throw new IllegalArgumentException("Block breaking time must be finite and non-negative");
    }

    public int textureLayer(int face) {
        return switch (face) {
            case 2 -> topTexture;
            case 3 -> bottomTexture;
            case 0, 1, 4, 5 -> sideTexture;
            default -> throw new IllegalArgumentException("Face index must be in [0, 6)");
        };
    }

    public BlockModel model() {
        if (shape.geometryIndex() >= 5) return new BlockModel(shape, .25f, .875f);
        return shape == BlockShape.SNOW_LAYER ? new BlockModel(shape, 0, .125f) : BlockModel.full(shape);
    }
}
