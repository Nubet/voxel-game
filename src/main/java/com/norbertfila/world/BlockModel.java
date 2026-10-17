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

    public float minX() {
        return switch (shape) {
            case VINE_EAST -> 15 / 16f;
            case COCOA_EAST -> .625f;
            case COCOA_SOUTH, COCOA_NORTH -> .25f;
            default -> 0;
        };
    }

    public float maxX() {
        return switch (shape) {
            case VINE_WEST -> 1 / 16f;
            case COCOA_WEST -> .375f;
            case COCOA_SOUTH, COCOA_NORTH -> .75f;
            default -> 1;
        };
    }

    public float minZ() {
        return switch (shape) {
            case VINE_SOUTH -> 15 / 16f;
            case COCOA_SOUTH -> .625f;
            case COCOA_EAST, COCOA_WEST -> .25f;
            default -> 0;
        };
    }

    public float maxZ() {
        return switch (shape) {
            case VINE_NORTH -> 1 / 16f;
            case COCOA_NORTH -> .375f;
            case COCOA_EAST, COCOA_WEST -> .75f;
            default -> 1;
        };
    }
}
