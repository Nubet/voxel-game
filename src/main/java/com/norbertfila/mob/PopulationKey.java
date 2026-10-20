package com.norbertfila.mob;

import java.util.Objects;

public record PopulationKey(MobCategory category, SpawnZone zone) {
    public PopulationKey {
        Objects.requireNonNull(category);
        Objects.requireNonNull(zone);
    }
}
