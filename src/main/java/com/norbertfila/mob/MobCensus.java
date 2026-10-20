package com.norbertfila.mob;

import java.util.List;
import java.util.Objects;

/** Population per category and zone against the cap that applies with the current number of active columns. */
public record MobCensus(List<Zone> zones, boolean naturalSpawning) {
    public static final MobCensus EMPTY = new MobCensus(List.of(), true);

    public record Zone(PopulationKey key, int count, int cap) {
        public Zone {
            Objects.requireNonNull(key);
        }
    }

    public MobCensus {
        zones = List.copyOf(zones);
    }
}
