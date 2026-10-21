package com.norbertfila.mob.ai;

import java.util.Objects;

/**
 * A mob's AI, split like Minecraft's: the target selector decides whom to fight and the goal selector decides what to
 * do about it, so new behaviors are new goals rather than edits to a state machine.
 */
public final class MobBrain {
    private final GoalSelector targets;
    private final GoalSelector goals;

    public MobBrain(GoalSelector targets, GoalSelector goals) {
        this.targets = Objects.requireNonNull(targets);
        this.goals = Objects.requireNonNull(goals);
    }

    void tick(MobContext context) {
        context.senseTarget();
        targets.tick(context);
        goals.tick(context);
        context.navigation().tick(context.mob(), context.world().terrain());
    }
}
