package com.norbertfila.player.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.Tool;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class HeldItemMotionTest {
    @Test
    void switchingLowersTheOldItemBeforeRaisingTheNewOne() {
        var motion = new HeldItemMotion();
        motion.update(BlockId.DIRT, 1, 0);
        assertEquals(0, motion.equip());
        byte sword = Tool.IRON_SWORD.id();
        motion.update(sword, 1, .05);
        assertEquals(BlockId.DIRT, motion.shownItem());
        assertEquals(.4, motion.equip(), 1e-6);
        motion.update(sword, 1, .05);
        motion.update(sword, 1, .05);
        assertEquals(sword, motion.shownItem());
        for (int frame = 0; frame < 10; frame++) motion.update(sword, 1, .05);
        assertEquals(0, motion.equip(), 1e-6);
    }

    @Test
    void anAttackDipsTheHandUntilTheSwingRecharges() {
        var motion = new HeldItemMotion();
        motion.update(BlockId.AIR, 1, 0);
        motion.update(BlockId.AIR, .5, .2);
        assertEquals(1 - .125, motion.equip(), 1e-6);
        assertEquals(BlockId.AIR, motion.shownItem());
        assertThrows(IllegalArgumentException.class, () -> motion.update(BlockId.AIR, 2, 0));
    }
}
