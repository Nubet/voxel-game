package com.norbertfila.mob.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobType;
import com.norbertfila.mob.ai.navigation.PathFinder;
import com.norbertfila.mob.ai.navigation.PathNavigator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoalSelectorTest {
    private final List<String> log = new ArrayList<>();
    private final AiTestWorld test = new AiTestWorld();
    private final MobContext context =
            new MobContext(test.spawn(MobType.ZOMBIE, 0, 0), new PathNavigator(new PathFinder(10)));

    GoalSelectorTest() {
        context.update(
                new WorldMobWorld(test.world, test.population, List.of(), test.explosion, test.random), test.random);
    }

    @Test
    void higherPriorityGoalsTakeSharedControlsAndDisjointGoalsRunTogether() {
        var stroll = new Probe("stroll", Set.of(Goal.Control.MOVE));
        var chase = new Probe("chase", Set.of(Goal.Control.MOVE, Goal.Control.LOOK));
        var watch = new Probe("watch", Set.of(Goal.Control.LOOK));
        var target = new Probe("target", Set.of(Goal.Control.TARGET));
        var selector = GoalSelector.builder()
                .add(3, stroll)
                .add(1, chase)
                .add(4, watch)
                .add(0, target)
                .build();
        stroll.wanted = true;
        watch.wanted = true;
        target.wanted = true;
        selector.tick(context);
        assertTrue(selector.running(stroll) && selector.running(watch) && selector.running(target));
        chase.wanted = true;
        selector.tick(context);
        assertTrue(selector.running(chase));
        assertFalse(selector.running(stroll));
        assertFalse(selector.running(watch));
        assertTrue(selector.running(target));
        assertTrue(log.contains("stop stroll") && log.contains("stop watch"));
        chase.wanted = false;
        selector.tick(context);
        assertFalse(selector.running(chase));
        assertTrue(selector.running(stroll));
        assertEquals(1, log.stream().filter("tick chase"::equals).count());
    }

    private final class Probe implements Goal {
        final String name;
        final Set<Control> controls;
        boolean wanted;

        Probe(String name, Set<Control> controls) {
            this.name = name;
            this.controls = controls;
        }

        public Set<Control> controls() {
            return controls;
        }

        public boolean canStart(MobContext context) {
            return wanted;
        }

        public void start(MobContext context) {
            log.add("start " + name);
        }

        public void stop(MobContext context) {
            log.add("stop " + name);
        }

        public void tick(MobContext context) {
            log.add("tick " + name);
        }
    }
}
