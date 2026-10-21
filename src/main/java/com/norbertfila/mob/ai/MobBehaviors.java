package com.norbertfila.mob.ai;

import com.norbertfila.mob.MobType;
import com.norbertfila.mob.ai.goal.LookAtPlayerGoal;
import com.norbertfila.mob.ai.goal.MeleeAttackGoal;
import com.norbertfila.mob.ai.goal.NearestTargetGoal;
import com.norbertfila.mob.ai.goal.RandomStrollGoal;

/** The goal lists that make each mob type behave like its Minecraft counterpart. */
public final class MobBehaviors {
    // Minecraft zombies deal 3 damage on normal difficulty.
    static final float ZOMBIE_DAMAGE = 3;

    private MobBehaviors() {}

    public static MobBrain brainFor(MobType type) {
        return switch (type) {
            case ZOMBIE -> hostile(GoalSelector.builder().add(2, new MeleeAttackGoal(1, ZOMBIE_DAMAGE)));
            case SKELETON, CREEPER -> hostile(GoalSelector.builder());
        };
    }

    /** Adds the idle behaviors every hostile mob shares below its combat goals. */
    private static MobBrain hostile(GoalSelector.Builder combat) {
        return new MobBrain(
                GoalSelector.builder().add(1, new NearestTargetGoal()).build(),
                combat.add(5, new RandomStrollGoal(.8))
                        .add(6, new LookAtPlayerGoal())
                        .build());
    }
}
