package com.norbertfila.mob.ai.goal;

import com.norbertfila.mob.ai.Goal;
import com.norbertfila.mob.ai.MobContext;
import com.norbertfila.mob.ai.navigation.PathFinder;
import java.util.Set;

/** Minecraft's RandomStrollGoal: an idle mob now and then walks to a random reachable spot nearby. */
public final class RandomStrollGoal implements Goal {
    // Minecraft starts a stroll with a 1 in 120 chance per 20 TPS tick.
    static final int START_CHANCE = 360;
    static final int HORIZONTAL_RANGE = 10;
    static final int VERTICAL_RANGE = 7;
    private static final int ATTEMPTS = 10;
    private final double speed;

    public RandomStrollGoal(double speed) {
        this.speed = speed;
    }

    @Override
    public Set<Control> controls() {
        return Set.of(Control.MOVE);
    }

    @Override
    public boolean canStart(MobContext context) {
        return context.target().isEmpty()
                && !context.navigation().active()
                && context.random().nextInt(START_CHANCE) == 0;
    }

    @Override
    public void start(MobContext context) {
        var mob = context.mob();
        var terrain = context.world().terrain();
        int height = (int) Math.ceil(mob.type().height());
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = (int) Math.floor(mob.x()) + context.random().nextInt(-HORIZONTAL_RANGE, HORIZONTAL_RANGE + 1);
            int z = (int) Math.floor(mob.z()) + context.random().nextInt(-HORIZONTAL_RANGE, HORIZONTAL_RANGE + 1);
            int baseY = (int) Math.floor(mob.y());
            for (int y = baseY + VERTICAL_RANGE; y >= baseY - VERTICAL_RANGE; y--)
                if (PathFinder.standable(terrain, x, y, z, height)) {
                    context.navigation().moveTo(x + .5, y, z + .5, speed, 0);
                    return;
                }
        }
    }

    @Override
    public boolean shouldContinue(MobContext context) {
        return context.target().isEmpty() && context.navigation().active();
    }

    @Override
    public void stop(MobContext context) {
        context.navigation().stop(context.mob());
    }
}
