package com.norbertfila.mob.ai;

import com.norbertfila.mob.MobType;
import com.norbertfila.mob.ai.goal.ChaseTargetGoal;
import com.norbertfila.mob.ai.goal.LookAtPlayerGoal;
import com.norbertfila.mob.ai.goal.MeleeAttackGoal;
import com.norbertfila.mob.ai.goal.NearestTargetGoal;
import com.norbertfila.mob.ai.goal.RandomStrollGoal;
import com.norbertfila.mob.ai.goal.RangedBowAttackGoal;
import com.norbertfila.mob.ai.goal.SwellGoal;

/** The goal lists that make each mob type behave like its Minecraft counterpart. */
public final class MobBehaviors {
    // Minecraft zombies deal 3 damage on normal difficulty.
    static final float ZOMBIE_DAMAGE = 3;
    static final float CREEPER_EXPLOSION_POWER = 3;
    static final double SKELETON_RANGE = 15;
    // Minecraft skeletons rest 40 of its ticks between shots below hard difficulty.
    static final int SKELETON_COOLDOWN = 120;

    private MobBehaviors() {}

    public static MobBrain brainFor(MobType type) {
        return switch (type) {
            case ZOMBIE -> hostile(GoalSelector.builder().add(2, new MeleeAttackGoal(1, ZOMBIE_DAMAGE)));
            case CREEPER ->
                hostile(GoalSelector.builder()
                        .add(2, new SwellGoal(CREEPER_EXPLOSION_POWER))
                        .add(4, new ChaseTargetGoal(1)));
            case SKELETON ->
                hostile(GoalSelector.builder()
                        .add(4, new RangedBowAttackGoal(1, SKELETON_RANGE, SKELETON_COOLDOWN, new SkeletonBow())));
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
