package com.norbertfila.mob.ai;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.explosion.Explosion;
import com.norbertfila.mob.*;
import com.norbertfila.projectile.ArrowSystem;
import com.norbertfila.world.*;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Flat stone floor at y = 0 spanning x and z from -32 to 47, with mobs, targets and a seeded random. */
class AiTestWorld {
    final World world = new World();
    final MobPopulation population = new MobPopulation();
    final List<AttackTarget> targets = new ArrayList<>();
    final SplittableRandom random = new SplittableRandom(1);
    final MobAi ai;
    final ArrowSystem arrows = new ArrowSystem();
    final List<BlockPos> destroyed = new ArrayList<>();
    final Explosion explosion = new Explosion(world, blocks -> {
        for (var position : blocks) world.setBlock(position, BlockId.AIR, 64);
        destroyed.addAll(blocks);
    });

    AiTestWorld() {
        this(MobAi.standard());
    }

    AiTestWorld(MobAi ai) {
        this.ai = ai;
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int y = -1; y <= 1; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
    }

    Mob spawn(MobType type, double x, double z) {
        return population.spawn(type, SpawnZone.SURFACE, x, 0, z, 0).orElseThrow();
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            ai.tick(population, new WorldMobWorld(world, population, targets, explosion, arrows, random), random);
            population.tick(world, 1 / 60.0);
            arrows.tick(world, targets, 1 / 60.0);
        }
    }
}
