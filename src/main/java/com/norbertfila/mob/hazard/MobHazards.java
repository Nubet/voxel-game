package com.norbertfila.mob.hazard;

import com.norbertfila.mob.DamageSource;
import com.norbertfila.mob.Mob;
import com.norbertfila.mob.MobPopulation;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.BlockPos;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Environmental damage, following Minecraft: undead mobs catch fire in direct daylight, water and rain put fires out,
 * and burning costs one health point per second. Mobs that run out of health are removed.
 */
public final class MobHazards {
    static final int SUNLIGHT_FIRE_TICKS = 8 * FixedSimulation.TICKS_PER_SECOND;
    static final int FIRE_DAMAGE_INTERVAL = FixedSimulation.TICKS_PER_SECOND;
    static final float FIRE_DAMAGE = 1;
    // Minecraft rolls (brightness - 0.4) * 2 / 30 per 20 TPS tick, 0.04 in full daylight; this tick is a third of that.
    static final double IGNITE_CHANCE = .04 / 3;

    /** Advances every mob one world tick and returns how many died. */
    public int tick(MobPopulation population, HazardEnvironment environment, RandomGenerator random) {
        Objects.requireNonNull(environment);
        int died = 0;
        for (var mob : population.mobs()) {
            var eye = eye(mob);
            if (environment.touchesWater(mob.body()) || mob.burning() && environment.rainingAt(eye)) mob.extinguish();
            else if (inSunlight(mob, eye, environment) && random.nextDouble() < IGNITE_CHANCE)
                mob.ignite(SUNLIGHT_FIRE_TICKS);
            if (mob.burning() && mob.burn() % FIRE_DAMAGE_INTERVAL == 0) mob.damage(DamageSource.FIRE, FIRE_DAMAGE);
            if (mob.dead()) {
                population.remove(mob);
                died++;
            }
        }
        return died;
    }

    private static boolean inSunlight(Mob mob, BlockPos eye, HazardEnvironment environment) {
        return mob.type().burnsInDaylight()
                && environment.daylight()
                && environment.seesSky(eye)
                && !environment.rainingAt(eye);
    }

    private static BlockPos eye(Mob mob) {
        return new BlockPos(
                (int) Math.floor(mob.x()), (int) Math.floor(mob.y() + mob.type().height() * .85), (int)
                        Math.floor(mob.z()));
    }
}
