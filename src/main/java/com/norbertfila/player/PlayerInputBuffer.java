package com.norbertfila.player;

import java.util.Objects;

public final class PlayerInputBuffer {
    private final double[] jumpPresses = new double[16];
    private PlayerInput held = PlayerInput.NONE;
    private int head;
    private int size;

    public void updateHeld(PlayerInput input) {
        Objects.requireNonNull(input);
        if (input.jumpPressed()) throw new IllegalArgumentException("Press events must be queued separately");
        held = input;
    }

    public void pressJump(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Jump press time must be finite and non-negative");
        }
        // Keep recent taps when streaming stalls simulation; replaying unbounded old input is undesirable.
        if (size == jumpPresses.length) {
            head = (head + 1) % jumpPresses.length;
            size--;
        }
        jumpPresses[(head + size) % jumpPresses.length] = timeSeconds;
        size++;
    }

    public PlayerInput nextTick() {
        PlayerInput input = peekTick();
        if (size != 0) {
            head = (head + 1) % jumpPresses.length;
            size--;
        }
        return input;
    }

    public PlayerInput peekTick() {
        if (size == 0) return held;
        double pressedAt = jumpPresses[head];
        return new PlayerInput(
                held.forward(),
                held.backward(),
                held.left(),
                held.right(),
                held.jumpHeld(),
                held.sneakHeld(),
                held.sprintHeld(),
                true,
                pressedAt);
    }

    public void clear() {
        held = PlayerInput.NONE;
        head = 0;
        size = 0;
    }
}
