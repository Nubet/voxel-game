package com.norbertfila.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.Tool;
import org.junit.jupiter.api.Test;

class MeleeAttackTest {
    @Test
    void scalesWeaponDamageByTheCharge() {
        var sword = MeleeWeapon.of(Tool.DIAMOND_SWORD.id());
        var charge = new AttackCharge();
        assertEquals(new MeleeAttack.Outcome(7, MeleeAttack.KNOCKBACK), MeleeAttack.resolve(sword, charge));
        charge.reset();
        assertEquals(7 * .20128f, MeleeAttack.resolve(sword, charge).damage(), 1e-5);
        charge.tick(.3);
        assertEquals(7 * .41632f, MeleeAttack.resolve(sword, charge).damage(), 1e-5);
    }
}
