package com.norbertfila.mob.ai;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.mob.Mob;
import com.norbertfila.mob.ai.navigation.PathNavigator;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** One mob's view of itself, its memory and the world, handed to every goal. */
public final class MobContext {
    private final Mob mob;
    private final PathNavigator navigation;
    private MobWorld world;
    private RandomGenerator random;
    private AttackTarget target;
    private int ticksSinceTargetSeen;
    private boolean targetVisible;

    MobContext(Mob mob, PathNavigator navigation) {
        this.mob = Objects.requireNonNull(mob);
        this.navigation = Objects.requireNonNull(navigation);
    }

    void update(MobWorld world, RandomGenerator random) {
        this.world = Objects.requireNonNull(world);
        this.random = Objects.requireNonNull(random);
    }

    public Mob mob() {
        return mob;
    }

    public PathNavigator navigation() {
        return navigation;
    }

    public MobWorld world() {
        return world;
    }

    public RandomGenerator random() {
        return random;
    }

    public Optional<AttackTarget> target() {
        return Optional.ofNullable(target);
    }

    public void setTarget(AttackTarget target) {
        if (target != this.target) {
            ticksSinceTargetSeen = 0;
            targetVisible = false;
        }
        this.target = target;
    }

    public int ticksSinceTargetSeen() {
        return ticksSinceTargetSeen;
    }

    /** Whether the mob saw its target this tick; the brain checks once per tick before goals run. */
    public boolean seesTarget() {
        return targetVisible;
    }

    void senseTarget() {
        if (target == null) {
            targetVisible = false;
            return;
        }
        targetVisible = world.canSee(mob.x(), mob.eyeY(), mob.z(), target.x(), target.eyeY(), target.z());
        ticksSinceTargetSeen = targetVisible ? 0 : ticksSinceTargetSeen + 1;
    }

    public double distanceToTarget() {
        if (target == null) return Double.POSITIVE_INFINITY;
        return distanceTo(target);
    }

    public double distanceTo(AttackTarget other) {
        double dx = other.x() - mob.x(), dy = other.y() - mob.y(), dz = other.z() - mob.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
