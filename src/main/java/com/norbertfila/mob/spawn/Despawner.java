package com.norbertfila.mob.spawn;

import com.norbertfila.mob.MobPopulation;
import com.norbertfila.mob.PlayerPosition;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Minecraft's despawn rules for naturally spawned mobs: gone at once beyond the despawn distance, never close to a
 * player, and in between only by chance after staying away from players for a while.
 */
public final class Despawner {
    private final SpawnSettings settings;

    public Despawner(SpawnSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    /** Runs once per spawn cycle and returns how many mobs were removed. */
    public int despawn(MobPopulation population, List<PlayerPosition> players, RandomGenerator random) {
        if (players.isEmpty()) return 0;
        int removed = 0;
        for (var mob : population.mobs()) {
            double nearest = PlayerPosition.nearest(players, mob.x(), mob.y(), mob.z());
            if (nearest > settings.despawnDistance()) {
                population.remove(mob);
                removed++;
            } else if (nearest < settings.randomDespawnDistance()) mob.markNearPlayer();
            else if (mob.ticksAwayFromPlayers() > settings.despawnDelayTicks()
                    && random.nextInt(settings.randomDespawnChance()) == 0) {
                population.remove(mob);
                removed++;
            }
        }
        return removed;
    }
}
