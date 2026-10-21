package com.norbertfila.mob.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Minecraft's goal selector: goals are kept in priority order (lower runs first). Each tick, running goals that should
 * no longer continue stop; then every idle goal that can start takes its controls, interrupting lower-priority goals
 * that hold them, unless a higher-priority goal already does.
 */
public final class GoalSelector {
    private record Entry(int priority, Goal goal) {}

    private final List<Entry> entries;
    private final List<Entry> running = new ArrayList<>();

    private GoalSelector(List<Entry> entries) {
        this.entries = entries.stream()
                .sorted(Comparator.comparingInt(Entry::priority))
                .toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        public Builder add(int priority, Goal goal) {
            entries.add(new Entry(priority, Objects.requireNonNull(goal)));
            return this;
        }

        public GoalSelector build() {
            return new GoalSelector(entries);
        }
    }

    public void tick(MobContext context) {
        for (var entry : List.copyOf(running)) if (!entry.goal().shouldContinue(context)) stop(entry, context);
        for (var entry : entries) {
            if (running.contains(entry) || !entry.goal().canStart(context)) continue;
            if (!claim(entry, context)) continue;
            running.add(entry);
            entry.goal().start(context);
        }
        for (var entry : List.copyOf(running))
            if (running.contains(entry)) entry.goal().tick(context);
    }

    public boolean running(Goal goal) {
        return running.stream().anyMatch(entry -> entry.goal() == goal);
    }

    public void stopAll(MobContext context) {
        for (var entry : List.copyOf(running)) stop(entry, context);
    }

    private boolean claim(Entry candidate, MobContext context) {
        var wanted = candidate.goal().controls();
        var holders = new ArrayList<Entry>();
        for (var entry : running) {
            if (entry.goal().controls().stream().noneMatch(wanted::contains)) continue;
            if (entry.priority() <= candidate.priority()) return false;
            holders.add(entry);
        }
        for (var holder : holders) stop(holder, context);
        return true;
    }

    private void stop(Entry entry, MobContext context) {
        running.remove(entry);
        entry.goal().stop(context);
    }
}
