package com.norbertfila.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealthTest {
    @Test
    void damageRespectsTheHurtCooldownAndStopsAtZero() {
        var health = new Health(20);
        assertTrue(health.damage(DamageSource.MOB_ATTACK, 3));
        assertFalse(health.damage(DamageSource.MOB_ATTACK, 3));
        assertEquals(17, health.value());
        for (int i = 0; i < Health.HURT_TICKS; i++) health.tick();
        assertTrue(health.damage(DamageSource.EXPLOSION, 50));
        assertEquals(0, health.value());
        assertTrue(health.dead());
        for (int i = 0; i < Health.HURT_TICKS; i++) health.tick();
        assertFalse(health.damage(DamageSource.FIRE, 1));
        health.restore();
        assertEquals(20, health.value());
        assertEquals(0, health.hurtTicks());
        assertThrows(IllegalArgumentException.class, () -> health.damage(DamageSource.FIRE, -1));
    }
}
