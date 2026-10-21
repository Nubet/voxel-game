package com.norbertfila.mob;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.Attacker;
import com.norbertfila.combat.MeleeAttack;
import com.norbertfila.items.Tool;
import com.norbertfila.player.PlayerCombat;
import java.util.List;
import org.junit.jupiter.api.Test;

class MobCombatTest {
    @Test
    void sprintKnockbackLandsEvenWhenTheHitOnlyTopsUpDuringTheHurtCooldown() {
        var mob = new MobPopulation()
                .spawn(MobType.ZOMBIE, SpawnZone.SURFACE, 2, 0, 0, 0)
                .orElseThrow();
        var standing = Attacker.standing(0, 0, 0, 0);
        assertTrue(new PlayerCombat(Tool.WOODEN_SWORD.id())
                .attack(mob, standing, area -> List.of())
                .isPresent());
        mob.velocityX = mob.velocityZ = 0;
        var sprinting = new Attacker(0, 0, 0, 90, true, false, true, false, 5.6);
        assertTrue(new PlayerCombat(Tool.DIAMOND_AXE.id())
                .attack(mob, sprinting, area -> List.of())
                .orElseThrow()
                .sprintHit());
        assertEquals(20 - 9, mob.health(), 1e-5);
        assertEquals(0, mob.velocityX, 1e-9);
        assertEquals(MeleeAttack.SPRINT_KNOCKBACK, mob.velocityZ, 1e-9);
    }
}
