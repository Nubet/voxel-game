package com.norbertfila.mob.spawn;

import com.norbertfila.mob.MobType;
import com.norbertfila.mob.PopulationKey;
import com.norbertfila.mob.SpawnZone;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record SpawnEntry(MobType type, int weight, int minGroup, int maxGroup, Set<SpawnZone> zones) {
    public SpawnEntry {
        Objects.requireNonNull(type);
        if (weight < 1) throw new IllegalArgumentException("Spawn weight must be positive");
        if (minGroup < 1 || maxGroup < minGroup) throw new IllegalArgumentException("Invalid spawn group size");
        if (zones.isEmpty()) throw new IllegalArgumentException("Spawn entry needs at least one zone");
        zones = Set.copyOf(EnumSet.copyOf(zones));
    }

    public static SpawnEntry everywhere(MobType type, int weight, int minGroup, int maxGroup) {
        return new SpawnEntry(type, weight, minGroup, maxGroup, EnumSet.allOf(SpawnZone.class));
    }

    public boolean matches(PopulationKey key) {
        return type.category() == key.category() && zones.contains(key.zone());
    }
}
