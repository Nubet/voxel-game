package com.norbertfila.render;

import com.norbertfila.combat.CriticalHitEvent;
import com.norbertfila.simulation.FixedSimulation;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/** Minecraft's crit emitter and particle motion, converted from 20 Hz ticks to seconds. */
final class CriticalHitParticles {
    static final int FLOATS_PER_VERTEX = SpriteBatch.FLOATS_PER_VERTEX;
    static final int WAVES = 3, ATTEMPTS_PER_WAVE = 16;
    static final double WAVE_SECONDS = .05;
    static final double MAX_LIFETIME = .5;
    private static final double DRAG = 20 * Math.log(1 / .7);
    private static final double GRAVITY = 8;
    private static final double SPEED = 8;

    private CriticalHitParticles() {}

    static float[] vertices(
            List<CriticalHitEvent> events,
            double seconds,
            double cameraX,
            double cameraY,
            double cameraZ,
            float[] right,
            float[] up) {
        var out = new float[events.size() * WAVES * ATTEMPTS_PER_WAVE * 6 * FLOATS_PER_VERTEX];
        int at = 0;
        for (var event : events) {
            double sinceHit = seconds - event.tick() * FixedSimulation.TICK_SECONDS;
            if (sinceHit < 0 || sinceHit >= (WAVES - 1) * WAVE_SECONDS + MAX_LIFETIME) continue;
            var body = event.body();
            double width = body.maxX() - body.minX(), height = body.maxY() - body.minY();
            double centerX = (body.minX() + body.maxX()) * .5, centerZ = (body.minZ() + body.maxZ()) * .5;
            var random = new SplittableRandom(event.tick() * 31 + Double.hashCode(centerX) ^ Double.hashCode(centerZ));
            for (int wave = 0; wave < WAVES; wave++)
                for (int attempt = 0; attempt < ATTEMPTS_PER_WAVE; attempt++) {
                    double dx = random.nextDouble(-1, 1), dy = random.nextDouble(-1, 1), dz = random.nextDouble(-1, 1);
                    double lifetime = 6 / (random.nextDouble() * .8 + .6) / 20;
                    float size = (float) (random.nextDouble(.1, .2) * .75);
                    double age = sinceHit - wave * WAVE_SECONDS;
                    if (dx * dx + dy * dy + dz * dz > 1 || age < 0 || age >= lifetime) continue;
                    double travel = (1 - Math.exp(-DRAG * age)) / DRAG;
                    double vy = (dy + .2) * SPEED;
                    double x = centerX + width * dx / 4 + dx * SPEED * travel;
                    double y = body.minY()
                            + height * (.5 + dy / 4)
                            + (vy + GRAVITY / DRAG) * travel
                            - GRAVITY / DRAG * age;
                    double z = centerZ + width * dz / 4 + dz * SPEED * travel;
                    at = quad(out, at, x - cameraX, y - cameraY, z - cameraZ, size / 2, right, up);
                }
        }
        return at == out.length ? out : Arrays.copyOf(out, at);
    }

    private static int quad(float[] out, int at, double x, double y, double z, float half, float[] right, float[] up) {
        float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (int corner : new int[] {0, 1, 2, 0, 2, 3}) {
            float sx = corners[corner][0] * half, sy = corners[corner][1] * half;
            out[at++] = (float) (x + right[0] * sx + up[0] * sy);
            out[at++] = (float) (y + right[1] * sx + up[1] * sy);
            out[at++] = (float) (z + right[2] * sx + up[2] * sy);
            out[at++] = corners[corner][2];
            out[at++] = corners[corner][3];
        }
        return at;
    }
}
