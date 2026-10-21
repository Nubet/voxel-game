package com.norbertfila.interaction;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.interaction.EntityTargeting.Ray;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.Aabb;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityTargetingTest {
    private static final Ray EAST = new Ray(0, 1.5, 0, 1, 0, 0);

    private record Dummy(Aabb body, boolean attackable) implements AttackTarget {
        static Dummy at(double x) {
            return new Dummy(new Aabb(x - .3, 0, -.3, x + .3, 1.95, .3), true);
        }

        @Override
        public double eyeHeight() {
            return 1.7;
        }

        @Override
        public boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ) {
            return false;
        }

        @Override
        public void knockback(double pushX, double pushZ) {}
    }

    @Test
    void picksTheNearestAttackableBodyWithinReach() {
        var near = Dummy.at(2.5);
        var far = Dummy.at(4);
        var dead = new Dummy(Dummy.at(1.5).body(), false);
        var hit = EntityTargeting.find(List.of(far, dead, near), EAST, 3).orElseThrow();
        assertSame(near, hit.target());
        assertEquals(2.2, hit.distance(), 1e-9);
        assertTrue(EntityTargeting.find(List.of(far), EAST, 3).isEmpty());
        assertSame(
                far, EntityTargeting.find(List.of(far), EAST, 5).orElseThrow().target());
    }

    @Test
    void missesBodiesBesideTheRayAndCountsAnOriginInsideAsZero() {
        var aside = new Dummy(new Aabb(1, 0, 1, 2, 2, 2), true);
        assertTrue(EntityTargeting.find(List.of(aside), EAST, 5).isEmpty());
        var around = Dummy.at(0);
        assertEquals(
                0, EntityTargeting.find(List.of(around), EAST, 3).orElseThrow().distance());
    }

    @Test
    void reachFollowsModernJavaEdition() {
        assertEquals(3, EntityTargeting.reach(Mode.SURVIVAL));
        assertEquals(5, EntityTargeting.reach(Mode.CREATIVE));
        assertEquals(0, EntityTargeting.reach(Mode.SPECTATOR));
        var look = Ray.look(0, 0, 0, 0, 0);
        assertEquals(1, look.directionX(), 1e-12);
        assertEquals(new Aabb(-.01, 1.49, -.01, 3.01, 1.51, .01), EntityTargeting.sweep(EAST, 3));
        assertThrows(IllegalArgumentException.class, () -> new Ray(0, 0, 0, 2, 0, 0));
    }
}
