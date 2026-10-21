package com.norbertfila.mob.ai.goal;

import com.norbertfila.mob.ai.Goal;
import com.norbertfila.mob.ai.MobContext;
import java.util.Set;

/**
 * Walks to the current target and keeps facing it, refreshing the path every few ticks like Minecraft's
 * MeleeAttackGoal. Subclasses add what happens on arrival.
 */
public class ChaseTargetGoal implements Goal {
    // Minecraft recomputes the chase path every 4 to 11 of its ticks.
    static final int MIN_REPATH_TICKS = 12, EXTRA_REPATH_TICKS = 21;
    static final double STOP_DISTANCE = .9;
    private final double speed;
    private int repathIn;

    public ChaseTargetGoal(double speed) {
        if (!(speed > 0)) throw new IllegalArgumentException("Chase speed must be positive");
        this.speed = speed;
    }

    @Override
    public Set<Control> controls() {
        return Set.of(Control.MOVE, Control.LOOK);
    }

    @Override
    public boolean canStart(MobContext context) {
        return context.target().filter(target -> target.attackable()).isPresent();
    }

    @Override
    public void start(MobContext context) {
        repathIn = 0;
    }

    @Override
    public void tick(MobContext context) {
        var target = context.target().orElseThrow();
        var mob = context.mob();
        mob.lookAt(target.x(), target.z());
        if (--repathIn <= 0) {
            repathIn = MIN_REPATH_TICKS + context.random().nextInt(EXTRA_REPATH_TICKS);
            context.navigation().moveTo(target.x(), target.y(), target.z(), speed, 0);
        }
        // Close the last stretch directly, because paths end at block centers, and halt at arm's length because
        // mobs do not push the target out of the way.
        double distance = context.distanceToTarget();
        if (distance < STOP_DISTANCE) {
            context.navigation().stop(mob);
            repathIn = 0;
        } else if (distance < 2 && context.seesTarget()) mob.walkToward(target.x(), target.z(), speed);
        whenChasing(context);
    }

    @Override
    public void stop(MobContext context) {
        context.navigation().stop(context.mob());
        context.mob().stopLooking();
    }

    protected void whenChasing(MobContext context) {}
}
