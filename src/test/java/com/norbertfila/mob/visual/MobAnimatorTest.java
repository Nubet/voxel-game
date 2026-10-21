package com.norbertfila.mob.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.MobRenderState;
import com.norbertfila.mob.MobType;
import org.junit.jupiter.api.Test;

class MobAnimatorTest {
    @Test
    void standingMobsHoldStillAndWalkingLegsSwingOpposite() {
        var still = MobAnimator.pose(MobRenderState.standing(1, MobType.ZOMBIE, 0, 0, 0, 0), 1);
        for (float pitch : still.pitch()) assertEquals(0, pitch, 1e-6);
        var walking = MobAnimator.pose(state(MobType.ZOMBIE, .3, .3 + 2.3 / 60, MobAction.NONE, 0), 1);
        float right = walking.pitch()[4], left = walking.pitch()[5];
        assertNotEquals(0, right, 1e-3);
        assertEquals(-right, left, 1e-6);
        var creeper = MobAnimator.pose(state(MobType.CREEPER, .3, .3 + 2.5 / 60, MobAction.NONE, 0), 1);
        assertEquals(creeper.pitch()[2], creeper.pitch()[5], 1e-6, "diagonal legs move together");
        assertEquals(-creeper.pitch()[2], creeper.pitch()[3], 1e-6);
    }

    @Test
    void skeletonsRaiseArmsToAimAndZombiesLungeWhenTheyHit() {
        var aiming = MobAnimator.pose(state(MobType.SKELETON, 0, 0, MobAction.AIMING, .5f), 1);
        assertEquals(Math.PI / 2, aiming.pitch()[3], .01);
        assertEquals(Math.PI / 2 + .1, aiming.pitch()[2], .01);
        var lunge = MobAnimator.pose(state(MobType.ZOMBIE, 0, 0, MobAction.ATTACKING, .12f), 1);
        assertTrue(lunge.pitch()[2] < -.3f);
        var resting = MobAnimator.pose(state(MobType.ZOMBIE, 0, 0, MobAction.ATTACKING, .8f), 1);
        assertEquals(0, resting.pitch()[2], 1e-6);
    }

    @Test
    void chargingCreepersSwellAndBlink() {
        var calm = MobAnimator.pose(state(MobType.CREEPER, 0, 0, MobAction.NONE, 0), 1);
        assertEquals(1, calm.scaleXZ());
        assertFalse(calm.flash());
        var late = MobAnimator.pose(state(MobType.CREEPER, 0, 0, MobAction.CHARGING, .95f), 1);
        assertTrue(late.scaleXZ() > 1.3f && late.scaleY() > 1);
        boolean blinked = false, dark = false;
        for (float progress = .05f; progress < 1; progress += .05f) {
            boolean flash = MobAnimator.pose(state(MobType.CREEPER, 0, 0, MobAction.CHARGING, progress), 1)
                    .flash();
            blinked |= flash;
            dark |= !flash;
        }
        assertTrue(blinked && dark);
    }

    private static MobRenderState state(MobType type, double from, double to, MobAction action, float progress) {
        return new MobRenderState(1, type, 0, 0, 0, 0, 0, 0, 0, 0, from, to, false, 0, action, progress);
    }
}
