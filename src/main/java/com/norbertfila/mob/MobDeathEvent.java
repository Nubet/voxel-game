package com.norbertfila.mob;

import com.norbertfila.world.Aabb;
import java.util.Objects;

/** A slain mob that finished its death animation and was removed. */
public record MobDeathEvent(long tick, Aabb body) {
    public MobDeathEvent {
        Objects.requireNonNull(body);
        if (tick < 0) throw new IllegalArgumentException("Invalid mob death tick");
    }
}
