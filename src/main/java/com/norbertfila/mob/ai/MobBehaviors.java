package com.norbertfila.mob.ai;

import com.norbertfila.mob.MobType;
import com.norbertfila.mob.ai.goal.LookAtPlayerGoal;
import com.norbertfila.mob.ai.goal.NearestTargetGoal;
import com.norbertfila.mob.ai.goal.RandomStrollGoal;

/** The goal lists that make each mob type behave like its Minecraft counterpart. */
public final class MobBehaviors {
    private MobBehaviors() {}

    public static MobBrain brainFor(MobType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, CREEPER ->
                new MobBrain(
                        GoalSelector.builder().add(1, new NearestTargetGoal()).build(),
                        GoalSelector.builder()
                                .add(5, new RandomStrollGoal(.8))
                                .add(6, new LookAtPlayerGoal())
                                .build());
        };
    }
}
