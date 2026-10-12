package com.norbertfila.world;

public final class BlockId {
    public static final byte AIR = 0,
            GRASS = 1,
            DIRT = 2,
            STONE = 3,
            SAND = 4,
            GRAVEL = 5,
            BEDROCK = 6,
            SNOW = 7,
            LOG = 8,
            LEAVES = 9,
            PLANKS = 10,
            COBBLE = 11,
            BRICKS = 12,
            GLASS = 13,
            SANDSTONE = 14,
            STONE_BRICKS = 15,
            WATER = 16;

    public static int toUnsignedInt(byte id) {
        return Byte.toUnsignedInt(id);
    }

    public static boolean isValid(byte id) {
        return switch (toUnsignedInt(id)) {
            case AIR,
                    GRASS,
                    DIRT,
                    STONE,
                    SAND,
                    GRAVEL,
                    BEDROCK,
                    SNOW,
                    LOG,
                    LEAVES,
                    PLANKS,
                    COBBLE,
                    BRICKS,
                    GLASS,
                    SANDSTONE,
                    STONE_BRICKS,
                    WATER -> true;
            default -> false;
        };
    }

    private BlockId() {}
}
