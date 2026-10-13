package com.norbertfila.core;

final class HeldPlacementCooldown {
    static final int REPEAT_DELAY_TICKS = 12;
    private int remaining;

    boolean ready() {
        return remaining == 0;
    }

    void attempted() {
        remaining = REPEAT_DELAY_TICKS;
    }

    void tick() {
        if (remaining > 0) remaining--;
    }
}
