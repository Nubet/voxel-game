package com.norbertfila.core;

final class HeldPlacementCooldown {
    static final int INITIAL_DELAY_TICKS = 12;
    static final int REPEAT_DELAY_TICKS = 4;
    private int remaining;

    boolean ready() {
        return remaining == 0;
    }

    void placedAfterClick() {
        remaining = INITIAL_DELAY_TICKS;
    }

    void placedAfterRepeat() {
        remaining = REPEAT_DELAY_TICKS;
    }

    void tick() {
        if (remaining > 0) remaining--;
    }
}
