package com.norbertfila.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.Tool;
import org.junit.jupiter.api.Test;

class MeleeAttackTest {
    private static final MeleeWeapon SWORD = MeleeWeapon.of(Tool.DIAMOND_SWORD.id());
    private static final Attacker STANDING = Attacker.standing(0, 0);
    private static final Attacker FALLING = new Attacker(0, 0, false, true, false, false);

    @Test
    void scalesWeaponDamageByTheCharge() {
        var charge = new AttackCharge();
        assertEquals(
                new MeleeAttack.Outcome(7, MeleeAttack.KNOCKBACK, false), MeleeAttack.resolve(SWORD, charge, STANDING));
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
            new Attacker(0, 0, false, false, false, false),
            new Attacker(0, 0, false, true, true, false),
            new Attacker(0, 0, false, true, false, true)
        }) assertFalse(MeleeAttack.resolve(SWORD, charge, blocked).critical());
        charge.reset();
        charge.tick(.5);
        assertFalse(MeleeAttack.resolve(SWORD, charge, FALLING).critical());
        charge.tick(.04);
        assertTrue(MeleeAttack.resolve(SWORD, charge, FALLING).critical());
    }
}
