package com.norbertfila.mob.ai.goal;

import com.norbertfila.combat.DamageSource;
import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.ai.MobContext;

/** Minecraft's zombie attack: chase the target and hit it whenever it is in reach and the cooldown has passed. */
public final class MeleeAttackGoal extends ChaseTargetGoal {
    // Minecraft attacks once per 20 of its ticks.
    static final int COOLDOWN_TICKS = 60;
    // Push in blocks per second, close to Minecraft's 0.4 blocks per tick after ground friction.
    static final double KNOCKBACK = 6;
    private final float damage;
    private int cooldown;

    public MeleeAttackGoal(double speed, float damage) {
        super(speed);
        if (!(damage >= 0)) throw new IllegalArgumentException("Damage must not be negative");
        this.damage = damage;
    }

    @Override
    public void start(MobContext context) {
        super.start(context);
        cooldown = 0;
    }

    @Override
    protected void whenChasing(MobContext context) {
        var mob = context.mob();
        var target = context.target().orElseThrow();
        if (cooldown > 0) cooldown--;
        mob.act(MobAction.ATTACKING, 1 - cooldown / (float) COOLDOWN_TICKS);
        if (cooldown > 0 || !context.seesTarget() || context.distanceToTarget() > reach(context)) return;
        double dx = target.x() - mob.x(), dz = target.z() - mob.z(), length = Math.max(1e-6, Math.hypot(dx, dz));
        target.hurt(DamageSource.MOB_ATTACK, damage, dx / length * KNOCKBACK, dz / length * KNOCKBACK);
        cooldown = COOLDOWN_TICKS;
    }

    @Override
    public void stop(MobContext context) {
        super.stop(context);
        context.mob().act(MobAction.NONE, 0);
    }

    /** Minecraft's reach: twice the attacker's width, squared, plus the target's width, under a square root. */
    private static double reach(MobContext context) {
        var target = context.target().orElseThrow().body();
        double width = context.mob().type().width();
        return Math.sqrt(4 * width * width + (target.maxX() - target.minX()));
    }
}
