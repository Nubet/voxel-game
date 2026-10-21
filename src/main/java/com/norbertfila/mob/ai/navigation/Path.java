package com.norbertfila.mob.ai.navigation;

import com.norbertfila.world.BlockPos;
import java.util.List;

/** Feet cells from the start to the goal, or to the closest reachable cell when the goal cannot be reached. */
public record Path(List<BlockPos> nodes, boolean reachesGoal) {
    public Path {
        nodes = List.copyOf(nodes);
        if (nodes.isEmpty()) throw new IllegalArgumentException("A path needs at least one node");
    }

    public BlockPos end() {
        return nodes.getLast();
    }
}
