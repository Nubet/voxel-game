package com.norbertfila.projectile;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.world.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrowSystemTest {
    private static final double TICK = 1 / 60.0;
    private final World world = flat();
    private final ArrowSystem arrows = new ArrowSystem();
    private final Dummy shooter = new Dummy(0, 0, 0);

    @Test
    void arrowsArcDownAndStickInTheGround() {
        arrows.shoot(shooter, 2, 2, 2, 20, 0, 0);
        run(List.of(), 10);
        var flying = arrows.renderSnapshot().getFirst();
        assertTrue(flying.y() < 2 && flying.x() > 4);
        assertTrue(flying.directionY() < 0, "the arrow points along its falling path");
        run(List.of(), 120);
        var stuck = arrows.renderSnapshot().getFirst();
        assertEquals(stuck.x(), stuck.previousX(), 1e-9);
        assertTrue(stuck.y() >= -.1 && stuck.y() < .1, "stuck in the ground at " + stuck.y());
        assertEquals(1, arrows.size());
        run(List.of(), ArrowSystem.LIFETIME_TICKS);
        assertEquals(0, arrows.size());
    }

    @Test
    void arrowsStopAtWalls() {
        for (int y = 0; y < 4; y++)
            for (int z = 0; z < 16; z++) world.setBlock(new BlockPos(8, y, z), BlockId.STONE, 64);
        arrows.shoot(shooter, 2, 2, 4, 32, 0, 0);
        run(List.of(), 30);
        assertTrue(arrows.renderSnapshot().getFirst().x() < 8);
    }

    @Test
    void arrowsHurtTheFirstTargetTheyCrossButNotTheShooter() {
        var target = new Dummy(10, 0, 4);
        arrows.shoot(target, 2, 1.5, 4, 32, 0, 0);
        arrows.shoot(shooter, 2, 1.5, 4, 32, 0, 0);
        run(List.of(target, shooter), 30);
        assertEquals(1, target.hits);
        assertEquals(DamageSource.ARROW, target.source);
        assertEquals(4, target.damage, "1.6 blocks per tick deals four damage");
        assertTrue(target.knockbackX > 0);
        assertEquals(1, arrows.size(), "the arrow shot by the target itself kept flying");
    }

    private void run(List<? extends AttackTarget> targets, int ticks) {
        for (int i = 0; i < ticks; i++) arrows.tick(world, targets, TICK);
    }

    private static World flat() {
        var world = new World();
        for (int x = -1; x <= 2; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = -1; y <= 1; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
        return world;
    }

    private static final class Dummy implements AttackTarget {
        final double x, y, z;
        int hits;
        float damage;
        DamageSource source;
        double knockbackX;

        Dummy(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Aabb body() {
            return new Aabb(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
        }

        public double eyeHeight() {
            return 1.62;
        }

        public boolean attackable() {
            return true;
        }

        public boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ) {
            hits++;
            damage = amount;
            this.source = source;
            this.knockbackX = knockbackX;
            return true;
        }

        @Override
        public void knockback(double pushX, double pushZ) {}
    }
}
