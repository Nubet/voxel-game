package com.norbertfila.player.visual;

/**
 * Minecraft's main hand height: the hand sinks while a swing recharges and while the selected item changes, and the
 * shown item is swapped only once the hand is almost out of view.
 */
public final class HeldItemMotion {
    /** 0.4 per 20 Hz tick. */
    static final double SPEED = 8;

    static final double SWAP_HEIGHT = .1;
    private double height;
    private byte shown;
    private boolean started;

    public void update(byte held, double charge, double seconds) {
        if (!Double.isFinite(charge) || charge < 0 || charge > 1 || !Double.isFinite(seconds) || seconds < 0)
            throw new IllegalArgumentException("Invalid held item motion step");
        double target = held == shown ? charge * charge * charge : 0;
        if (!started) {
            started = true;
            shown = held;
            height = charge * charge * charge;
            return;
        }
        height += Math.clamp(target - height, -SPEED * seconds, SPEED * seconds);
        if (height < SWAP_HEIGHT) shown = held;
    }

    public byte shownItem() {
        return shown;
    }

    /** 0 when the hand is fully raised, 1 when it is out of view. */
    public float equip() {
        return (float) (1 - height);
    }
}
