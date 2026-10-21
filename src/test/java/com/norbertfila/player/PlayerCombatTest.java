package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.Attacker;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.combat.MeleeAttack;
import com.norbertfila.items.Tool;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerCombatTest {
    private static final Attacker ORIGIN = Attacker.standing(0, 0, 0, 0);

    private static final class Target implements AttackTarget {
        final List<float[]> hits = new ArrayList<>();
        final double x, z;

        Target(double x, double z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public Aabb body() {
            return new Aabb(x - .3, 0, z - .3, x + .3, 1.95, z + .3);
        }

        @Override
        public double eyeHeight() {
            return 1.7;
        }

        @Override
        public boolean attackable() {
            return true;
        }

        @Override
        public boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ) {
            assertEquals(DamageSource.PLAYER_ATTACK, source);
            hits.add(new float[] {amount, (float) knockbackX, (float) knockbackZ});
            return true;
        }
    }

    private static List<Target> none(Aabb ignored) {
        return List.of();
    }

    @Test
    void hitsWithTheHeldWeaponAndPushesAwayFromTheAttacker() {
        var combat = new PlayerCombat(Tool.IRON_AXE.id());
        var target = new Target(2, 0);
        assertTrue(combat.attack(target, ORIGIN, PlayerCombatTest::none).isPresent());
        assertArrayEquals(new float[] {9, (float) MeleeAttack.KNOCKBACK, 0}, target.hits.getFirst(), 1e-5f);
        combat.attack(target, ORIGIN, PlayerCombatTest::none);
        assertEquals(9 * .2f, target.hits.get(1)[0], .05f);
    }

    @Test
    void sprintHitsAlsoPushAlongTheAttackersFacing() {
        var combat = new PlayerCombat(Tool.STONE_SWORD.id());
        var target = new Target(2, 0);
        var sprinting = new Attacker(0, 0, 0, 90, true, false, true, false, 5.6);
        assertTrue(combat.attack(target, sprinting, PlayerCombatTest::none)
                .orElseThrow()
                .sprintHit());
        float[] hit = target.hits.getFirst();
        assertEquals(MeleeAttack.KNOCKBACK, hit[1], 1e-5);
        assertEquals(MeleeAttack.SPRINT_KNOCKBACK, hit[2], 1e-5);
    }

    @Test
    void sweepsHitBodiesAroundTheTargetWithinReachOfTheAttacker() {
        var combat = new PlayerCombat(Tool.WOODEN_SWORD.id());
        var target = new Target(2, 0);
        var beside = new Target(2, 1);
        var beyond = new Target(3.2, 0);
        assertTrue(combat.attack(target, ORIGIN, area -> List.of(target, beside, beyond))
                .orElseThrow()
                .sweep());
        assertEquals(4, target.hits.getFirst()[0]);
        assertArrayEquals(
                new float[] {MeleeAttack.SWEEP_DAMAGE, (float) MeleeAttack.KNOCKBACK, 0},
                beside.hits.getFirst(),
                1e-5f);
        assertTrue(beyond.hits.isEmpty());
        assertEquals(1, target.hits.size());
    }

    @Test
    void missesAndItemSwitchesSpendTheCharge() {
        var combat = new PlayerCombat(BlockId.AIR);
        assertEquals(1, combat.charge());
        combat.miss();
        assertTrue(combat.charge() < .2);
        combat.tick(.25, BlockId.AIR);
        assertEquals(1, combat.charge());
        combat.tick(1 / 60d, Tool.STONE_AXE.id());
        assertTrue(combat.charge() < .1);
    }
}
