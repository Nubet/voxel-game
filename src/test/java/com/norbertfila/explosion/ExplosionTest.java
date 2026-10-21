package com.norbertfila.explosion;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.world.*;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ExplosionTest {
    private final World world = ground(BlockId.DIRT);
    private final List<BlockPos> removed = new ArrayList<>();
    private final Explosion explosion = new Explosion(world, blocks -> {
        removed.addAll(blocks);
        for (var block : blocks) world.setBlock(block, BlockId.AIR, 64);
    });

    @Test
    void blastsACraterThatStoneResistsMoreThanDirt() {
        explosion.explode(8.5, 0, 8.5, 3, List.of(), new SplittableRandom(1));
        int dirt = removed.size();
        assertTrue(dirt > 20, "dirt crater: " + dirt);
        assertTrue(removed.contains(new BlockPos(8, -1, 8)));
        removed.clear();
        var stone = ground(BlockId.STONE);
        new Explosion(stone, removed::addAll).explode(8.5, 0, 8.5, 3, List.of(), new SplittableRandom(1));
        assertTrue(removed.size() < dirt / 2, "stone crater: " + removed.size());
        assertTrue(removed.stream().allMatch(p -> Math.abs(p.x() - 8) <= 4 && p.y() >= -4));
    }

    @Test
    void bedrockAndFluidsSurvive() {
        world.setBlock(new BlockPos(8, -1, 8), BlockId.BEDROCK, 64);
        world.setBlock(new BlockPos(9, -1, 8), BlockId.WATER, 64);
        explosion.explode(8.5, 0, 8.5, 3, List.of(), new SplittableRandom(2));
        assertFalse(removed.contains(new BlockPos(8, -1, 8)));
        assertFalse(removed.contains(new BlockPos(9, -1, 8)));
    }

    @Test
    void damageFallsWithDistanceAndWallsShieldTargets() {
        var near = new Target(10.5, 0, 8.5);
        var far = new Target(13.5, 0, 8.5);
        var outside = new Target(20.5, 0, 8.5);
        var result = explosion.explode(8.5, 0, 8.5, 3, List.of(near, far, outside), new SplittableRandom(3));
        assertEquals(2, result.targetsHit());
        assertTrue(near.damage > far.damage && far.damage > 0);
        assertEquals(0, outside.damage);
        assertEquals(DamageSource.EXPLOSION, near.source);
        assertTrue(near.knockbackX > 0);
        var shielded = ground(BlockId.STONE);
        for (int z = 0; z < 16; z++)
            for (int y = 0; y < 4; y++) shielded.setBlock(new BlockPos(10, y, z), BlockId.BEDROCK, 64);
        var hidden = new Target(11.5, 0, 8.5);
        new Explosion(shielded, blocks -> {}).explode(8.5, 0, 8.5, 3, List.of(hidden), new SplittableRandom(4));
        assertEquals(0, hidden.damage);
    }

    private static World ground(byte block) {
        var world = new World();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = -1; y <= 1; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(block) : new Chunk());
        return world;
    }

    private static final class Target implements AttackTarget {
        final double x, y, z;
        float damage;
        DamageSource source;
        double knockbackX;

        Target(double x, double y, double z) {
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
            this.source = source;
            damage += amount;
            this.knockbackX = knockbackX;
            return true;
        }
    }
}
