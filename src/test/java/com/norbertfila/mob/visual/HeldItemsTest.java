package com.norbertfila.mob.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.MobType;
import org.junit.jupiter.api.Test;

class HeldItemsTest {
    @Test
    void theBowGripSitsInTheHandWithTheTipForwardAndTheLimbsUpright() {
        float[] quad = HeldItems.quad();
        assertEquals(HeldItems.VERTICES * HeldItems.FLOATS_PER_VERTEX, quad.length);
        for (int i = 0; i < quad.length; i += HeldItems.FLOATS_PER_VERTEX) assertEquals(0, quad[i], "a flat sprite");
        // The texel corner at the top left is the arrow tip, so it lies furthest along the arm.
        float tipY = Float.MAX_VALUE, stringZ = 0;
        for (int i = 0; i < quad.length; i += HeldItems.FLOATS_PER_VERTEX) {
            if (quad[i + 3] == 0 && quad[i + 4] == 0) tipY = quad[i + 1];
            if (quad[i + 3] == 1 && quad[i + 4] == 0) stringZ = quad[i + 2];
        }
        assertTrue(tipY < HeldItems.HAND_Y, "the tip points past the hand");
        assertTrue(stringZ < 0, "the top of the bow points up once the arm is raised");
    }

    @Test
    void pullFramesFollowMinecraftThresholds() {
        assertEquals(0, HeldItems.frame(MobAction.NONE, 0));
        assertEquals(1, HeldItems.frame(MobAction.AIMING, .3f));
        assertEquals(2, HeldItems.frame(MobAction.AIMING, .7f));
        assertEquals(3, HeldItems.frame(MobAction.AIMING, .95f));
        assertEquals(
                4,
                MobModels.of(MobType.SKELETON)
                        .heldItem()
                        .orElseThrow()
                        .textures()
                        .size());
        assertTrue(MobModels.of(MobType.ZOMBIE).heldItem().isEmpty());
    }
}
