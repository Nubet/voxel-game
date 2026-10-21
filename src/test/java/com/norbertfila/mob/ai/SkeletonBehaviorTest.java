package com.norbertfila.mob.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.DamageSource;
import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.MobType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import org.junit.jupiter.api.Test;

class SkeletonBehaviorTest {
    private final AiTestWorld test = new AiTestWorld();

    @Test
    void skeletonsDrawAndShootArrowsThatHitTheirTarget() {
        var skeleton = test.spawn(MobType.SKELETON, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 18.5);
        test.targets.add(player);
        boolean aimed = false;
        for (int tick = 0; tick < 1200 && player.hits.size() < 2; tick++) {
            test.run(1);
            aimed |= skeleton.action() == MobAction.AIMING;
        }
        assertTrue(aimed, "never drew the bow");
        assertTrue(player.hits.size() >= 2, "arrows that hit: " + player.hits.size());
        assertEquals(DamageSource.ARROW, player.hits.getFirst().source());
        assertTrue(player.hits.getFirst().amount() >= 2);
    }

    @Test
    void skeletonsKeepTheirDistanceInsteadOfClosingIn() {
        var skeleton = test.spawn(MobType.SKELETON, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 12.5);
        test.targets.add(player);
        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < 900; tick++) {
            test.run(1);
            closest = Math.min(closest, Math.hypot(skeleton.x() - player.x, skeleton.z() - player.z));
        }
        assertTrue(closest > 1.5, "walked into melee range: " + closest);
        assertTrue(Math.hypot(skeleton.x() - player.x, skeleton.z() - player.z) > 3, "did not back away");
    }

    @Test
    void farTargetsAreApproachedAndHiddenOnesAreNotShot() {
        var skeleton = test.spawn(MobType.SKELETON, 8.5, 0.5);
        var player = new FakeTarget(8.5, 0, 15.5);
        test.targets.add(player);
        test.run(1);
        var context = test.ai.contextOf(skeleton).orElseThrow();
        for (int tick = 0; tick < 600 && context.target().isEmpty(); tick++) test.run(1);
        assertTrue(context.target().isPresent());
        double before = skeleton.z();
        for (int x = -32; x < 48; x++)
            for (int y = 0; y < 4; y++) test.world.setBlock(new BlockPos(x, y, 10), BlockId.STONE, 64);
        double furthest = before;
        for (int tick = 0; tick < 600; tick++) {
            test.run(1);
            furthest = Math.max(furthest, skeleton.z());
        }
        assertTrue(player.hits.isEmpty(), "shot through a wall");
        assertTrue(test.arrows.size() <= 1, "kept shooting at a wall: " + test.arrows.size());
        assertTrue(furthest > before + 3, "did not approach the hidden target");
    }
}
