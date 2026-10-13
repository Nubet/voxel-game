package com.norbertfila.core;

final class HeldPlacementCooldown {
    static final int REPEAT_DELAY_TICKS = 4;
    private int remaining;

    boolean ready() {
        return remaining == 0;
    }

    void placed() {
        remaining = REPEAT_DELAY_TICKS;
    }

    void tick() {
        if (remaining > 0) remaining--;
    }
}
