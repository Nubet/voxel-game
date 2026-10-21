package com.norbertfila.mob.ai.goal;

import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.ai.Goal;
import com.norbertfila.mob.ai.MobContext;
import java.util.Set;

/**
 * The creeper fuse from Minecraft's SwellGoal: close to a visible target the creeper stops, hisses and swells; it
 * calms down again when the target escapes beyond seven blocks or out of sight, and explodes when the fuse is full.
 */
public final class SwellGoal implements Goal {
    // Minecraft's fuse is 30 of its ticks, one and a half seconds.
    static final int FUSE_TICKS = 90;
    static final double START_DISTANCE = 3;
    static final double ESCAPE_DISTANCE = 7;
    private final float power;
    private int swell;

    public SwellGoal(float power) {
        if (!(power > 0)) throw new IllegalArgumentException("Explosion power must be positive");
        this.power = power;
    }

    @Override
    public Set<Control> controls() {
        return Set.of(Control.MOVE);
    }

    @Override
    public boolean canStart(MobContext context) {
        return swell > 0
                || context.target().filter(target -> target.attackable()).isPresent()
                        && context.distanceToTarget() < START_DISTANCE;
    }

    @Override
    public void start(MobContext context) {
        context.navigation().stop(context.mob());
    }

    @Override
    public void tick(MobContext context) {
        var mob = context.mob();
        var target = context.target();
        boolean fuseLit = target.isPresent()
                && target.get().attackable()
                && context.distanceToTarget() <= ESCAPE_DISTANCE
                && context.seesTarget();
        if (target.isPresent()) mob.lookAt(target.get().x(), target.get().z());
        swell = Math.max(0, swell + (fuseLit ? 1 : -1));
        mob.act(swell > 0 ? MobAction.CHARGING : MobAction.NONE, Math.min(1, swell / (float) FUSE_TICKS));
        if (swell >= FUSE_TICKS) {
            context.world().explode(mob.x(), mob.y(), mob.z(), power);
            mob.kill();
            swell = 0;
        }
    }

    @Override
    public void stop(MobContext context) {
        context.mob().act(MobAction.NONE, 0);
        context.mob().stopLooking();
    }

    public int swell() {
        return swell;
    }
}
