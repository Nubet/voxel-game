package com.norbertfila.combat;

import com.norbertfila.world.Aabb;
import java.util.Objects;

public record CriticalHitEvent(long tick, Aabb body) {
    public CriticalHitEvent {
        Objects.requireNonNull(body);
        if (tick < 0) throw new IllegalArgumentException("Invalid critical hit tick");
    }
}
