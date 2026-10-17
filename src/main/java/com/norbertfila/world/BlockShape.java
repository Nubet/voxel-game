package com.norbertfila.world;

public enum BlockShape {
    CUBE,
    CROSS,
    SNOW_LAYER,
    VINE_EAST,
    VINE_WEST,
    VINE_SOUTH,
    VINE_NORTH,
    COCOA_EAST,
    COCOA_WEST,
    COCOA_SOUTH,
    COCOA_NORTH;

    public int geometryIndex() {
        return ordinal() < VINE_EAST.ordinal() ? 0 : ordinal() - VINE_EAST.ordinal() + 1;
    }
}
