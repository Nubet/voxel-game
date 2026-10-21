package com.norbertfila.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.Tool;
import org.junit.jupiter.api.Test;

class MeleeAttackTest {
    private static final MeleeWeapon SWORD = MeleeWeapon.of(Tool.DIAMOND_SWORD.id());
    private static final Attacker STANDING = Attacker.standing(0, 0, 0, 0);
    private static final Attacker FALLING = new Attacker(0, 0, 0, 0, false, true, false, false, 0);

    @Test
    void scalesWeaponDamageByTheCharge() {
        var charge = new AttackCharge();
        assertEquals(
                new MeleeAttack.Outcome(7, MeleeAttack.KNOCKBACK, 0, false, true),
                MeleeAttack.resolve(SWORD, charge, STANDING));
        charge.reset();
        assertEquals(7 * .20128f, MeleeAttack.resolve(SWORD, charge, STANDING).damage(), 1e-5);
        charge.tick(.3);
        assertEquals(7 * .41632f, MeleeAttack.resolve(SWORD, charge, STANDING).damage(), 1e-5);
    }

    @Test
    void fallingWithAnAlmostFullChargeLandsACriticalHit() {
        var charge = new AttackCharge();
        var critical = MeleeAttack.resolve(SWORD, charge, FALLING);
        assertTrue(critical.critical());
        assertEquals(10.5f, critical.damage(), 1e-5);
        assertEquals(
                13.5f,
                MeleeAttack.resolve(MeleeWeapon.of(Tool.DIAMOND_AXE.id()), charge, FALLING)
                        .damage(),
                1e-5);
        for (var blocked : new Attacker[] {
            STANDING,
            new Attacker(0, 0, 0, 0, false, false, false, false, 0),
            new Attacker(0, 0, 0, 0, false, true, true, false, 0),
            new Attacker(0, 0, 0, 0, false, true, false, true, 0)
        }) assertFalse(MeleeAttack.resolve(SWORD, charge, blocked).critical());
        charge.reset();
        charge.tick(.5);
        assertFalse(MeleeAttack.resolve(SWORD, charge, FALLING).critical());
        charge.tick(.04);
        assertTrue(MeleeAttack.resolve(SWORD, charge, FALLING).critical());
    }

    @Test
    void sprintingWithAnAlmostFullChargeAddsKnockback() {
        var sprinting = new Attacker(0, 0, 0, 0, true, false, true, false, 5);
        var charge = new AttackCharge();
        var hit = MeleeAttack.resolve(SWORD, charge, sprinting);
        assertTrue(hit.sprintHit());
        assertEquals(MeleeAttack.SPRINT_KNOCKBACK, hit.sprintKnockback());
        assertFalse(hit.critical());
        assertFalse(MeleeAttack.resolve(SWORD, charge, STANDING).sprintHit());
        charge.reset();
        assertFalse(MeleeAttack.resolve(SWORD, charge, sprinting).sprintHit());
    }

    @Test
    void onlyAStillGroundedFullyChargedSwordSweeps() {
        var charge = new AttackCharge();
        assertTrue(MeleeAttack.resolve(SWORD, charge, STANDING).sweep());
        assertFalse(MeleeAttack.resolve(MeleeWeapon.of(Tool.DIAMOND_AXE.id()), charge, STANDING)
                .sweep());
        assertFalse(MeleeAttack.resolve(SWORD, charge, FALLING).sweep());
        assertFalse(MeleeAttack.resolve(SWORD, charge, new Attacker(0, 0, 0, 0, true, false, false, false, 4.3))
                .sweep());
        assertTrue(MeleeAttack.resolve(SWORD, charge, new Attacker(0, 0, 0, 0, true, false, false, false, 1.3))
                .sweep());
        charge.reset();
        assertFalse(MeleeAttack.resolve(SWORD, charge, STANDING).sweep());
    }
}
