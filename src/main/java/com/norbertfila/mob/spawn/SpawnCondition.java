package com.norbertfila.mob.spawn;

@FunctionalInterface
public interface SpawnCondition {
    boolean test(SpawnAttempt attempt);
}
