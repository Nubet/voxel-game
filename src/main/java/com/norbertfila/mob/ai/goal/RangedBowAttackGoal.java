package com.norbertfila.mob.ai.goal;

import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.ai.Goal;
import com.norbertfila.mob.ai.MobContext;
import java.util.Set;

/**
 * Minecraft's RangedBowAttackGoal for skeletons: approach until the target is in range and has been in sight for a
 * moment, then strafe around it, backing off when it comes close, while drawing the bow and loosing an arrow once the
 * bow is fully drawn and the target is visible. Losing sight for too long cancels the draw.
 */
public final class RangedBowAttackGoal implements Goal {
    // Minecraft durations, in its 20 TPS ticks, tripled for this simulation.
    static final int SIGHT_TICKS_BEFORE_STRAFE = 60;
    static final int UNSEEN_TICKS_BEFORE_GIVING_UP_DRAW = 180;
    static final int FULL_DRAW_TICKS = 60;
    static final int STRAFE_DIRECTION_TICKS = 60;
    private final double speed;
    private final double range;
    private final int cooldownTicks;
    private final Bow bow;
    private int seeTime, strafeTime = -1, drawTicks = -1, cooldown;
    private boolean clockwise, backwards;

    /** How the goal fires: aim from the mob's eyes toward a point. */
    @FunctionalInterface
    public interface Bow {
        void shoot(MobContext context, double targetX, double targetY, double targetZ);
    }

    public RangedBowAttackGoal(double speed, double range, int cooldownTicks, Bow bow) {
        if (!(speed > 0) || !(range > 0) || cooldownTicks < 0) throw new IllegalArgumentException("Invalid bow goal");
        this.speed = speed;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.bow = java.util.Objects.requireNonNull(bow);
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
        seeTime = 0;
        strafeTime = -1;
        drawTicks = -1;
    }

    @Override
    public void stop(MobContext context) {
        context.navigation().stop(context.mob());
        context.mob().stopLooking();
        context.mob().act(MobAction.NONE, 0);
        drawTicks = -1;
    }

    @Override
    public void tick(MobContext context) {
        var mob = context.mob();
        var target = context.target().orElseThrow();
        boolean sees = context.seesTarget();
        if (sees != seeTime > 0) seeTime = 0;
        seeTime += sees ? 1 : -1;
        double distance = context.distanceToTarget();
        if (distance <= range && seeTime >= SIGHT_TICKS_BEFORE_STRAFE) {
            context.navigation().stop(mob);
            strafeTime++;
        } else {
            context.navigation().moveTo(target.x(), target.y(), target.z(), speed, 0);
            strafeTime = -1;
        }
        if (strafeTime >= STRAFE_DIRECTION_TICKS) {
            if (context.random().nextFloat() < .3f) clockwise = !clockwise;
            if (context.random().nextFloat() < .3f) backwards = !backwards;
            strafeTime = 0;
        }
        if (strafeTime > -1) {
            if (distance > range * .75) backwards = false;
            else if (distance < range * .25) backwards = true;
            strafe(context, target.x(), target.z());
        }
        mob.lookAt(target.x(), target.z());
        if (cooldown > 0) cooldown--;
        if (drawTicks < 0) {
            if (cooldown == 0 && seeTime >= -UNSEEN_TICKS_BEFORE_GIVING_UP_DRAW) drawTicks = 0;
        } else if (!sees && seeTime < -UNSEEN_TICKS_BEFORE_GIVING_UP_DRAW) {
            drawTicks = -1;
        } else if (sees && ++drawTicks >= FULL_DRAW_TICKS) {
            // Aim at a third of the target's height, as Minecraft skeletons do.
            var body = target.body();
            bow.shoot(context, target.x(), body.minY() + (body.maxY() - body.minY()) / 3, target.z());
            drawTicks = -1;
            cooldown = cooldownTicks;
        }
        mob.act(
                drawTicks >= 0 ? MobAction.AIMING : MobAction.NONE,
                drawTicks >= 0 ? Math.min(1, drawTicks / (float) FULL_DRAW_TICKS) : 0);
    }

    private void strafe(MobContext context, double targetX, double targetZ) {
        var mob = context.mob();
        double forwardX = targetX - mob.x(), forwardZ = targetZ - mob.z();
        double length = Math.max(1e-6, Math.hypot(forwardX, forwardZ));
        forwardX /= length;
        forwardZ /= length;
        double ahead = backwards ? -.5 : .5, side = clockwise ? .5 : -.5;
        double moveX = forwardX * ahead - forwardZ * side, moveZ = forwardZ * ahead + forwardX * side;
        double moveLength = Math.hypot(moveX, moveZ);
        mob.walk(moveX / moveLength, moveZ / moveLength, speed * .5);
    }

    public boolean drawing() {
        return drawTicks >= 0;
    }
}
