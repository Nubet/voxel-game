package com.norbertfila.mob.spawn;

import static com.norbertfila.mob.spawn.SpawnConditions.*;

import java.util.List;

/** Conditions checked in order, so cheap ones should come before the light lookups. */
public record SpawnRules(List<SpawnCondition> conditions) {
    public static final SpawnRules HOSTILE = new SpawnRules(List.of(
            playerDistance(),
            solidGround(),
            noFluid(),
            freeSpace(),
            noMobOverlap(),
            allowedZone(),
            zoneHasRoom(),
            nightOnSurface(),
            darkness()));

    public SpawnRules {
        conditions = List.copyOf(conditions);
    }

    public boolean allows(SpawnAttempt attempt) {
        for (var condition : conditions) if (!condition.test(attempt)) return false;
        return true;
    }
}
