package com.norbertfila.persistence.world;

import com.norbertfila.player.PlayerController.Mode;
import java.util.Objects;

public record SavedPlayerState(double x, double y, double z, float yaw, float pitch, Mode mode, boolean flying) {
    public SavedPlayerState {
        Objects.requireNonNull(mode);
        if (!coordinate(x)
                || !coordinate(y)
                || !coordinate(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)
                || pitch < -90
                || pitch > 90) throw new IllegalArgumentException("Invalid saved player position/orientation");
        if (flying && mode != Mode.CREATIVE)
            throw new IllegalArgumentException("Only Creative has a toggleable flight state");
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && value >= Integer.MIN_VALUE + 2.0 && value <= Integer.MAX_VALUE - 2.0;
    }
}
