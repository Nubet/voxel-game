package com.norbertfila.mob.ai.navigation;

import com.norbertfila.mob.Mob;
import com.norbertfila.world.BlockPos;
import java.util.Objects;

/**
 * Walks one mob along a path: steers toward each node, jumps for steps, repaths when a moving target drifts or the
 * mob stops making progress, and gives up after repeated failures.
 */
public final class PathNavigator {
    static final int REPATH_INTERVAL_TICKS = 30;
    static final int STUCK_TICKS = 40;
    static final int MAX_FAILED_REPATHS = 3;
    private static final double NODE_REACHED = .45;
    private static final double TARGET_DRIFT = 1.5;
    private final PathFinder finder;
    private Path path;
    private int index, sinceRepath, stuckTicks, failedRepaths;
    private double targetX, targetY, targetZ, speed, reach, closestToNode;
    private boolean active;

    public PathNavigator(PathFinder finder) {
        this.finder = Objects.requireNonNull(finder);
    }

    /** Starts or retargets movement; a target that moved only a little keeps the current path. */
    public void moveTo(double x, double y, double z, double speedFactor, double reach) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !(speedFactor > 0) || !(reach >= 0))
            throw new IllegalArgumentException("Invalid navigation target");
        boolean drifted = !active || Math.hypot(x - targetX, z - targetZ) > TARGET_DRIFT || Math.abs(y - targetY) > 1;
        targetX = x;
        targetY = y;
        targetZ = z;
        speed = speedFactor;
        this.reach = reach;
        if (drifted && sinceRepath >= REPATH_INTERVAL_TICKS) path = null;
        if (!active) failedRepaths = 0;
        active = true;
    }

    public void stop(Mob mob) {
        active = false;
        path = null;
        mob.stop();
    }

    /** True while the navigator still has somewhere to go. */
    public boolean active() {
        return active;
    }

    public Path path() {
        return path;
    }

    public void tick(Mob mob, PathTerrain terrain) {
        if (!active) return;
        sinceRepath++;
        if (path == null && !repath(mob, terrain)) return;
        var node = path.nodes().get(index);
        double centerX = node.x() + .5, centerZ = node.z() + .5;
        double distance = Math.hypot(centerX - mob.x(), centerZ - mob.z());
        if (distance < NODE_REACHED && Math.abs(node.y() - mob.y()) < 1.1) {
            if (++index >= path.nodes().size()) {
                stop(mob);
                return;
            }
            node = path.nodes().get(index);
            centerX = node.x() + .5;
            centerZ = node.z() + .5;
            distance = Math.hypot(centerX - mob.x(), centerZ - mob.z());
            closestToNode = distance;
            stuckTicks = 0;
        }
        mob.walkToward(centerX, centerZ, speed);
        if (node.y() > mob.y() + .5 && mob.onGround() || mob.horizontalCollision() && mob.onGround()) mob.jump();
        if (distance < closestToNode - .05) {
            closestToNode = distance;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_TICKS) {
            path = null;
            if (++failedRepaths > MAX_FAILED_REPATHS) stop(mob);
        }
    }

    private boolean repath(Mob mob, PathTerrain terrain) {
        var start = new BlockPos((int) Math.floor(mob.x()), (int) Math.floor(mob.y() + .01), (int) Math.floor(mob.z()));
        var goal = new BlockPos((int) Math.floor(targetX), (int) Math.floor(targetY), (int) Math.floor(targetZ));
        var found = finder.find(terrain, start, goal, (int) Math.ceil(mob.type().height()), reach);
        sinceRepath = 0;
        stuckTicks = 0;
        closestToNode = Double.MAX_VALUE;
        if (found.isEmpty() || found.get().nodes().size() < 2) {
            stop(mob);
            return false;
        }
        path = found.get();
        index = 1;
        return true;
    }
}
