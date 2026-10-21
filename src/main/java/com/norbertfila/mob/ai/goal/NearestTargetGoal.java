package com.norbertfila.mob.ai.goal;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.mob.ai.Goal;
import com.norbertfila.mob.ai.MobContext;
import java.util.Set;

/**
 * Minecraft's NearestAttackableTargetGoal: now and then the mob looks for the closest attackable target in follow range
 * that it can see, and keeps it until it leaves follow range, stops being attackable, or stays unseen too long.
 */
public final class NearestTargetGoal implements Goal {
    // Minecraft rolls 1 in 10 per 20 TPS tick before searching, which is about every half second.
    static final int SEARCH_CHANCE = 30;
    // Minecraft forgets an unseen target after 60 of its ticks.
    static final int UNSEEN_MEMORY_TICKS = 180;

    @Override
    public Set<Control> controls() {
        return Set.of(Control.TARGET);
    }

    @Override
    public boolean canStart(MobContext context) {
        if (context.target().isPresent() || context.random().nextInt(SEARCH_CHANCE) != 0) return false;
        return nearest(context) != null;
    }

    @Override
    public void start(MobContext context) {
        context.setTarget(nearest(context));
    }

    @Override
    public boolean shouldContinue(MobContext context) {
        var target = context.target();
        return target.isPresent()
                && target.get().attackable()
                && context.distanceToTarget() <= followRange(context)
                && context.ticksSinceTargetSeen() < UNSEEN_MEMORY_TICKS;
    }

    @Override
    public void stop(MobContext context) {
        context.setTarget(null);
    }

    private static AttackTarget nearest(MobContext context) {
        var mob = context.mob();
        AttackTarget best = null;
        double bestDistance = followRange(context);
        for (var target : context.world().targets()) {
            if (!target.attackable()) continue;
            double distance = context.distanceTo(target);
            if (distance > bestDistance) continue;
            if (!context.world().canSee(mob.x(), mob.eyeY(), mob.z(), target.x(), target.eyeY(), target.z())) continue;
            best = target;
            bestDistance = distance;
        }
        return best;
    }

    private static double followRange(MobContext context) {
        return context.mob().type().attributes().followRange();
    }
}
