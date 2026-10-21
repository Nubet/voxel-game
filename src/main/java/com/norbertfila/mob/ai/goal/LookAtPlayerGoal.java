package com.norbertfila.mob.ai.goal;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.mob.ai.Goal;
import com.norbertfila.mob.ai.MobContext;
import java.util.Set;

/** Minecraft's LookAtPlayerGoal: an idle mob sometimes turns to watch a nearby player for a few seconds. */
public final class LookAtPlayerGoal implements Goal {
    static final double RANGE = 8;
    // Minecraft looks with a 2% chance per 20 TPS tick for 40 to 80 of its ticks.
    static final int START_CHANCE = 150;
    static final int MIN_TICKS = 120, EXTRA_TICKS = 120;
    private AttackTarget watched;
    private int ticksLeft;

    @Override
    public Set<Control> controls() {
        return Set.of(Control.LOOK);
    }

    @Override
    public boolean canStart(MobContext context) {
        if (context.target().isPresent() || context.random().nextInt(START_CHANCE) != 0) return false;
        watched = null;
        for (var player : context.world().targets())
            if (context.distanceTo(player) <= RANGE
                    && (watched == null || context.distanceTo(player) < context.distanceTo(watched))) watched = player;
        return watched != null;
    }

    @Override
    public void start(MobContext context) {
        ticksLeft = MIN_TICKS + context.random().nextInt(EXTRA_TICKS);
    }

    @Override
    public boolean shouldContinue(MobContext context) {
        return ticksLeft > 0 && context.target().isEmpty() && context.distanceTo(watched) <= RANGE * 1.5;
    }

    @Override
    public void tick(MobContext context) {
        ticksLeft--;
        context.mob().lookAt(watched.x(), watched.z());
    }

    @Override
    public void stop(MobContext context) {
        context.mob().stopLooking();
    }
}
