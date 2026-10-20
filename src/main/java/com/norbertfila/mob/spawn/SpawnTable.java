package com.norbertfila.mob.spawn;

import com.norbertfila.mob.MobCategory;
import com.norbertfila.mob.MobType;
import com.norbertfila.mob.PopulationKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

public final class SpawnTable {
    public static final SpawnTable OVERWORLD = new SpawnTable(List.of(
            SpawnEntry.everywhere(MobType.ZOMBIE, 95, 4, 4),
            SpawnEntry.everywhere(MobType.SKELETON, 100, 4, 4),
            SpawnEntry.everywhere(MobType.CREEPER, 100, 4, 4)));

    private final List<SpawnEntry> entries;

    public SpawnTable(List<SpawnEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<SpawnEntry> entries() {
        return entries;
    }

    public Optional<SpawnEntry> pick(PopulationKey key, RandomGenerator random) {
        Objects.requireNonNull(key);
        return pick(entry -> entry.matches(key), random);
    }

    public Optional<SpawnEntry> pick(MobCategory category, RandomGenerator random) {
        Objects.requireNonNull(category);
        return pick(entry -> entry.type().category() == category, random);
    }

    private Optional<SpawnEntry> pick(Predicate<SpawnEntry> filter, RandomGenerator random) {
        int total = 0;
        for (var entry : entries) if (filter.test(entry)) total += entry.weight();
        if (total == 0) return Optional.empty();
        int roll = random.nextInt(total);
        for (var entry : entries) {
            if (!filter.test(entry)) continue;
            roll -= entry.weight();
            if (roll < 0) return Optional.of(entry);
        }
        throw new AssertionError();
    }
}
