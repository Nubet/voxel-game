package com.norbertfila.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.Tool;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class MeleeWeaponTest {
    @Test
    void toolsUseMinecraftJavaDamageAndAttackSpeed() {
        assertEquals(new MeleeWeapon(4, 1.6f, true), MeleeWeapon.of(Tool.WOODEN_SWORD.id()));
        assertEquals(new MeleeWeapon(7, 1.6f, true), MeleeWeapon.of(Tool.DIAMOND_SWORD.id()));
        assertEquals(new MeleeWeapon(7, .8f, false), MeleeWeapon.of(Tool.WOODEN_AXE.id()));
        assertEquals(new MeleeWeapon(9, .8f, false), MeleeWeapon.of(Tool.STONE_AXE.id()));
        assertEquals(new MeleeWeapon(9, .9f, false), MeleeWeapon.of(Tool.IRON_AXE.id()));
        assertEquals(new MeleeWeapon(9, 1, false), MeleeWeapon.of(Tool.DIAMOND_AXE.id()));
        assertEquals(new MeleeWeapon(2, 1.2f, false), MeleeWeapon.of(Tool.WOODEN_PICKAXE.id()));
        assertEquals(new MeleeWeapon(5, 1.2f, false), MeleeWeapon.of(Tool.DIAMOND_PICKAXE.id()));
        assertEquals(MeleeWeapon.HAND, MeleeWeapon.of(BlockId.AIR));
        assertEquals(MeleeWeapon.HAND, MeleeWeapon.of(BlockId.STONE));
    }

    @Test
    void cooldownIsTheInverseOfAttackSpeedInSeconds() {
        assertEquals(.625, MeleeWeapon.of(Tool.IRON_SWORD.id()).cooldownSeconds(), 1e-6);
        assertEquals(1.25, MeleeWeapon.of(Tool.STONE_AXE.id()).cooldownSeconds(), 1e-6);
        assertEquals(.25, MeleeWeapon.HAND.cooldownSeconds(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeapon(1, 0, false));
    }
}
