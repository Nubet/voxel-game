package com.norbertfila.world;

public record BlockModel(BlockShape shape, float minY, float maxY) {
    public BlockModel {
        java.util.Objects.requireNonNull(shape);
        if (!Float.isFinite(minY) || !Float.isFinite(maxY) || minY < 0 || maxY > 1 || minY >= maxY)
            throw new IllegalArgumentException("Invalid model bounds");
    }

    public static BlockModel full(BlockShape shape) {
        return new BlockModel(shape, 0, 1);
    }
}
