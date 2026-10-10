package com.norbertfila.world;

public final class BlockId {
    public static final byte AIR = 0, GRASS = 1, DIRT = 2, STONE = 3;

    public static int toUnsignedInt(byte id) {
        return Byte.toUnsignedInt(id);
    }

    public static boolean isValid(byte id) {
        return switch (toUnsignedInt(id)) {
            case AIR, GRASS, DIRT, STONE -> true;
            default -> false;
        };
    }

    private BlockId() {}
}
