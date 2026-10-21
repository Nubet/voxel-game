package com.norbertfila.mob.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import org.junit.jupiter.api.Test;

class BasicBehaviorTest {
    private final AiTestWorld test = new AiTestWorld();

    @Test
    void idleMobsWanderAroundOnTheirOwn() {
        var creeper = test.spawn(MobType.CREEPER, 8.5, 8.5);
        double moved = 0;
        for (int i = 0; i < 60 && moved < 1; i++) {
            test.run(60);
            moved = Math.hypot(creeper.x() - 8.5, creeper.z() - 8.5);
        }
        assertTrue(moved >= 1, "the creeper never strolled");
    }

    @Test
    void mobsTargetVisibleAttackablePlayersInFollowRangeOnly() {
        var skeleton = test.spawn(MobType.SKELETON, 8.5, 8.5);
        var hidden = new FakeTarget(8.5, 0, 20.5);
        for (int x = -32; x < 48; x++)
            for (int y = 0; y < 4; y++) test.world.setBlock(new BlockPos(x, y, 15), BlockId.STONE, 64);
        test.targets.add(hidden);
        var context = contextOf(skeleton);
        test.run(300);
        assertTrue(context.target().isEmpty(), "targeted through a wall");
        var creative = new FakeTarget(8.5, 0, 12.5);
        creative.attackable = false;
        test.targets.add(creative);
        test.run(300);
        assertTrue(context.target().isEmpty(), "targeted a creative player");
        var visible = new FakeTarget(8.5, 0, 13.5);
        test.targets.add(visible);
        test.run(300);
        assertSame(visible, context.target().orElseThrow());
        visible.x = 8.5 + 40;
        test.run(5);
        assertTrue(context.target().isEmpty(), "kept a target beyond follow range");
    }

    private MobContext contextOf(com.norbertfila.mob.Mob mob) {
        test.run(1);
        return test.ai.contextOf(mob).orElseThrow();
    }
}
