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
    private static final Attacker ORIGIN = Attacker.standing(0, 0);

    private static final class Target implements AttackTarget {
        final List<float[]> hits = new ArrayList<>();

        @Override
        public Aabb body() {
            return new Aabb(1.7, 0, -.3, 2.3, 1.95, .3);
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

    @Test
    void hitsWithTheHeldWeaponAndPushesAwayFromTheAttacker() {
        var combat = new PlayerCombat(Tool.IRON_SWORD.id());
        var target = new Target();
        assertTrue(combat.attack(target, ORIGIN));
        assertArrayEquals(new float[] {6, (float) MeleeAttack.KNOCKBACK, 0}, target.hits.getFirst(), 1e-5f);
        combat.attack(target, ORIGIN);
        assertEquals(6 * .2f, target.hits.get(1)[0], .05f);
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
