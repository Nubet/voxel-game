package com.norbertfila.mob.spawn;

import com.norbertfila.mob.MobPopulation;
import com.norbertfila.mob.PlayerPosition;
import com.norbertfila.mob.PopulationKey;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public record SpawnContext(
        SpawnEnvironment environment,
        MobPopulation population,
        List<PlayerPosition> players,
        SpawnSettings settings,
        RandomGenerator random,
        int activeColumns) {
    public SpawnContext {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(population);
        players = List.copyOf(players);
        Objects.requireNonNull(settings);
        Objects.requireNonNull(random);
        if (activeColumns < 0) throw new IllegalArgumentException("Column count is negative");
    }

    public boolean hasRoom(PopulationKey key) {
        return population.count(key) < settings.scaledCap(key, activeColumns);
    }
}
