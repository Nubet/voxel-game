package com.norbertfila.render;

import com.norbertfila.interaction.BlockBreakEvent;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.BlockRegistry;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.SplittableRandom;

final class BlockDebris {
    static final int PARTICLES_PER_BLOCK = 24;
    static final int FLOATS_PER_PARTICLE = 8;
    static final double MAX_LIFETIME = .65;

    static int write(
            FloatBuffer target,
            List<BlockBreakEvent> events,
            double seconds,
            double cameraX,
            double cameraY,
            double cameraZ) {
        int count = 0;
        for (var event : events) {
            double age = seconds - event.tick() * FixedSimulation.TICK_SECONDS;
            if (age < 0 || age >= MAX_LIFETIME) continue;
            var block = event.block();
            var random = new SplittableRandom(event.tick() ^ ((long) block.hashCode() << 32) ^ event.blockId());
            var definition = BlockRegistry.DEFAULT.definition((byte) event.blockId());
            for (int index = 0; index < PARTICLES_PER_BLOCK; index++) {
                double x = (index % 3 + .5) / 3, y = (index / 3 % 2 + .5) / 2, z = (index / 6 + .5) / 4;
                double vx = (x - .5) * 3 + random.nextDouble(-.4, .4);
                double vy = random.nextDouble(.5, 2.2), vz = (z - .5) * 3 + random.nextDouble(-.4, .4);
                double lifetime = random.nextDouble(.4, MAX_LIFETIME);
                float u = random.nextInt(4) * .25f, v = random.nextInt(4) * .25f;
                int layer = definition.textureLayer(random.nextInt(6));
                float size = (float) random.nextDouble(.07, .13);
                if (age >= lifetime) continue;
                target.put((float) (block.x() - cameraX + x + vx * age));
                target.put((float) (block.y() - cameraY + y + vy * age - 4 * age * age));
                target.put((float) (block.z() - cameraZ + z + vz * age));
                target.put(u).put(v).put(layer).put(size).put((float) Math.min(1, (lifetime - age) / .15));
                count++;
            }
        }
        return count;
    }
}
