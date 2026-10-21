package com.norbertfila.render;

import com.norbertfila.mob.MobDeathEvent;
import com.norbertfila.simulation.FixedSimulation;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Minecraft's poof when a dead mob is removed, converted from 20 Hz ticks to seconds: twenty smoke puffs inside the
 * body drift slowly, rise and shrink through the sprite frames as they age.
 */
final class PoofParticles {
    static final int FLOATS_PER_VERTEX = SpriteBatch.FLOATS_PER_VERTEX;
    static final int PARTICLES = 20, FRAMES = 8;
    static final double MAX_LIFETIME = (16 / .2 + 2) / 20;
    private static final double DRAG = 20 * Math.log(1 / .9);
    private static final double LIFT = .004 * 20 * 20;

    private PoofParticles() {}

    /** Camera-relative quads grouped by sprite frame, largest puff first. */
    static float[][] vertices(
            List<MobDeathEvent> events,
            double seconds,
            double cameraX,
            double cameraY,
            double cameraZ,
            float[] right,
            float[] up) {
        var out = new float[FRAMES][events.size() * PARTICLES * 6 * FLOATS_PER_VERTEX];
        var used = new int[FRAMES];
        for (var event : events) {
            double age = seconds - event.tick() * FixedSimulation.TICK_SECONDS;
            if (age < 0 || age >= MAX_LIFETIME) continue;
            var body = event.body();
            double width = body.maxX() - body.minX(), height = body.maxY() - body.minY();
            double centerX = (body.minX() + body.maxX()) * .5, centerZ = (body.minZ() + body.maxZ()) * .5;
            var random = new SplittableRandom(event.tick() * 31 + Double.hashCode(centerX) ^ Double.hashCode(centerZ));
            for (int index = 0; index < PARTICLES; index++) {
                double x = centerX + width * random.nextDouble(-1, 1);
                double y = body.minY() + height * random.nextDouble();
                double z = centerZ + width * random.nextDouble(-1, 1);
                double vx = (random.nextGaussian() * .02 + random.nextDouble(-.05, .05)) * 20;
                double vy = (random.nextGaussian() * .02 + random.nextDouble(-.05, .05)) * 20;
                double vz = (random.nextGaussian() * .02 + random.nextDouble(-.05, .05)) * 20;
                float half = (float) (.1 * (random.nextDouble() * random.nextDouble() * 6 + 1));
                double lifetime = ((int) (16 / (random.nextDouble() * .8 + .2)) + 2) / 20.0;
                if (age >= lifetime) continue;
                double travel = (1 - Math.exp(-DRAG * age)) / DRAG;
                x += vx * travel;
                y += LIFT / DRAG * age + (vy - LIFT / DRAG) * travel;
                z += vz * travel;
                int frame = (int) (age / lifetime * (FRAMES - 1) + .5);
                used[frame] = CriticalHitParticles.quad(
                        out[frame], used[frame], x - cameraX, y - cameraY, z - cameraZ, half, right, up);
            }
        }
        for (int frame = 0; frame < FRAMES; frame++) out[frame] = Arrays.copyOf(out[frame], used[frame]);
        return out;
    }
}
