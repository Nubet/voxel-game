package com.norbertfila.world;

public final class WaterState {
    public static final byte EMPTY = -1;
    public static final byte SOURCE = 0;
    public static final byte FALLING = 8;
    public static final int MAX_DISTANCE = 7;

    private WaterState() {}

    public static byte flowing(int distance) {
        if (distance < 1 || distance > MAX_DISTANCE)
            throw new IllegalArgumentException("Water distance must be in [1, 7]: " + distance);
        return (byte) distance;
    }

    public static int distance(byte state) {
        validate(BlockId.WATER, state);
        return state & MAX_DISTANCE;
    }

    public static byte defaultFor(byte block) {
        return BlockId.isFluid(Byte.toUnsignedInt(block)) ? SOURCE : EMPTY;
    }

    public static void validate(byte block, byte state) {
        if (!BlockId.isValid(block))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(block));
        if (BlockId.isFluid(Byte.toUnsignedInt(block)) ? state < SOURCE || state > FALLING : state != EMPTY)
            throw new IllegalArgumentException(
                    "Invalid water state " + state + " for block " + BlockId.toUnsignedInt(block));
    }
}
