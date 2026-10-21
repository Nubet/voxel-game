package com.norbertfila.mob.ai;

import com.norbertfila.mob.Mob;
import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.MobPopulation;
import com.norbertfila.mob.MobType;
import com.norbertfila.mob.ai.navigation.PathFinder;
import com.norbertfila.mob.ai.navigation.PathNavigator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/** Runs every mob's brain once per world tick, before physics turns the resulting intents into motion. */
public final class MobAi {
    /** Bounds the A* search so a single unreachable target cannot stall the world thread. */
    static final int PATH_NODE_BUDGET = 1500;

    private record Mind(MobBrain brain, MobContext context) {}

    private final Function<MobType, MobBrain> brains;
    private final Map<Long, Mind> minds = new HashMap<>();

    public MobAi(Function<MobType, MobBrain> brains) {
        this.brains = Objects.requireNonNull(brains);
    }

    public static MobAi standard() {
        return new MobAi(MobBehaviors::brainFor);
    }

    public void tick(MobPopulation population, MobWorld world, RandomGenerator random) {
        var alive = new HashSet<Long>();
        for (Mob mob : population.mobs()) {
            if (mob.dead()) {
                mob.stop();
                mob.act(MobAction.NONE, 0);
                continue;
            }
            alive.add(mob.id());
            var mind = minds.computeIfAbsent(
                    mob.id(),
                    id -> new Mind(
                            brains.apply(mob.type()),
                            new MobContext(mob, new PathNavigator(new PathFinder(PATH_NODE_BUDGET)))));
            mind.context().update(world, random);
            mind.brain().tick(mind.context());
        }
        minds.keySet().retainAll(alive);
    }

    Optional<MobContext> contextOf(Mob mob) {
        return Optional.ofNullable(minds.get(mob.id())).map(Mind::context);
    }
}
