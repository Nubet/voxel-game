package com.norbertfila.player;

public record PlayerInput(
        boolean forward,
        boolean backward,
        boolean left,
        boolean right,
        boolean jumpHeld,
        boolean sneakHeld,
        boolean sprintHeld,
        boolean jumpPressed,
        double jumpPressedAtSeconds) {
    public static final PlayerInput NONE = new PlayerInput(false, false, false, false, false, false, false);

    public PlayerInput {
        if (!Double.isFinite(jumpPressedAtSeconds) || jumpPressedAtSeconds < 0) {
            throw new IllegalArgumentException("Jump press time must be finite and non-negative");
        }
    }

    public PlayerInput(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean jumpHeld,
            boolean sneakHeld,
            boolean sprintHeld) {
        this(forward, backward, left, right, jumpHeld, sneakHeld, sprintHeld, false, 0);
    }
}
