package com.norbertfila.mob.ai;

import java.util.Set;

/**
 * One behavior, modeled on Minecraft's goals. A {@link GoalSelector} runs the highest-priority goals whose controls do
 * not clash, so a chase can take over walking from a stroll while a look goal keeps turning the head.
 */
public interface Goal {
    enum Control {
        MOVE,
        LOOK,
        TARGET
    }

    Set<Control> controls();

    boolean canStart(MobContext context);

    default boolean shouldContinue(MobContext context) {
        return canStart(context);
    }

    default void start(MobContext context) {}

    default void stop(MobContext context) {}

    default void tick(MobContext context) {}
}
