package com.norbertfila.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.Health.Hit;
import org.junit.jupiter.api.Test;

class HealthTest {
    @Test
    void damageRespectsTheHurtCooldownAndStopsAtZero() {
        var health = new Health(20);
        assertEquals(Hit.FRESH, health.damage(DamageSource.MOB_ATTACK, 3));
        assertEquals(Hit.IGNORED, health.damage(DamageSource.MOB_ATTACK, 3));
        assertEquals(17, health.value());
        for (int i = 0; i < Health.HURT_TICKS; i++) health.tick();
        assertEquals(Hit.FRESH, health.damage(DamageSource.EXPLOSION, 50));
        assertEquals(0, health.value());
        assertTrue(health.dead());
        for (int i = 0; i < Health.HURT_TICKS; i++) health.tick();
        assertEquals(Hit.IGNORED, health.damage(DamageSource.FIRE, 1));
        health.restore();
        assertEquals(20, health.value());
        assertEquals(0, health.hurtTicks());
        assertThrows(IllegalArgumentException.class, () -> health.damage(DamageSource.FIRE, -1));
    }

    @Test
    void strongerHitsDuringTheCooldownDealOnlyTheDifference() {
        var health = new Health(20);
        assertEquals(Hit.FRESH, health.damage(DamageSource.PLAYER_ATTACK, 5));
        for (int i = 0; i < 12; i++) health.tick();
        assertEquals(Hit.IGNORED, health.damage(DamageSource.PLAYER_ATTACK, 4));
        assertEquals(15, health.value());
        assertEquals(Hit.TOP_UP, health.damage(DamageSource.PLAYER_ATTACK, 7));
        assertEquals(13, health.value());
        assertEquals(Health.HURT_TICKS - 12, health.hurtTicks());
        assertEquals(Hit.IGNORED, health.damage(DamageSource.PLAYER_ATTACK, 7));
        for (int i = 0; i < Health.HURT_TICKS; i++) health.tick();
        assertEquals(Hit.FRESH, health.damage(DamageSource.PLAYER_ATTACK, 2));
        assertEquals(11, health.value());
    }
}
