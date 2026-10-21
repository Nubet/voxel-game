package com.norbertfila.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.Tool;
import org.junit.jupiter.api.Test;

class AttackChargeTest {
    private static final MeleeWeapon SWORD = MeleeWeapon.of(Tool.DIAMOND_SWORD.id());

    @Test
    void startsFullyCharged() {
        var charge = new AttackCharge();
        assertEquals(1, charge.charge(SWORD));
        assertEquals(1, charge.multiplier(SWORD));
    }

    @Test
    void damageGrowsWithTheSquareOfTheChargeMeasuredInSeconds() {
        var charge = new AttackCharge();
        charge.reset();
        assertEquals(.04, charge.charge(SWORD), 1e-9);
        assertEquals(.20128, charge.multiplier(SWORD), 1e-9);
        for (int tick = 0; tick < 18; tick++) charge.tick(1 / 60d);
        assertEquals(.52, charge.charge(SWORD), 1e-9);
        assertEquals(.41632, charge.multiplier(SWORD), 1e-9);
        for (int tick = 0; tick < 18; tick++) charge.tick(1 / 60d);
        assertEquals(1, charge.charge(SWORD));
        assertEquals(1, charge.multiplier(SWORD));
    }

    @Test
    void slowerWeaponsNeedLongerToRecharge() {
        var charge = new AttackCharge();
        charge.reset();
        charge.tick(.6);
        assertEquals(1, charge.charge(SWORD));
        assertEquals(.5, charge.charge(MeleeWeapon.of(Tool.STONE_AXE.id())), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> charge.tick(-1));
        assertThrows(IllegalArgumentException.class, () -> charge.tick(Double.NaN));
    }
}
