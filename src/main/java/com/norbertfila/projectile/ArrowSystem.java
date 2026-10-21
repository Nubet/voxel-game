package com.norbertfila.projectile;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.interaction.VoxelRaycaster;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minecraft arrows as physical projectiles: they fall, slow down in air and much more in water, stick in the first
 * block they hit and hurt the first target they cross, harder the faster they fly.
 */
public final class ArrowSystem {
    public static final int MAX_ARROWS = 512;
    // Minecraft arrows deal their speed in blocks per tick times 2, rounded up.
    static final double BASE_DAMAGE = 2;
    // Minecraft pulls arrows down by 0.05 blocks per tick squared at 20 ticks per second.
    static final double GRAVITY = 20;
    // Minecraft keeps 0.99 of an arrow's speed per tick in air and 0.6 in water.
    static final double AIR_DRAG = -Math.log(.99) * 20;
    static final double WATER_DRAG = -Math.log(.6) * 20;
    // Minecraft discards arrows after a minute.
    static final int LIFETIME_TICKS = 60 * FixedSimulation.TICKS_PER_SECOND;
    static final double KNOCKBACK = 6;
    private static final double HIT_MARGIN = .3;
    private final List<Arrow> arrows = new ArrayList<>();
    private final VoxelRaycaster raycaster = new VoxelRaycaster();
    private long nextId;

    /** Launches an arrow; velocity is in blocks per second. Returns false when the arrow limit is reached. */
    public boolean shoot(AttackTarget shooter, double x, double y, double z, double vx, double vy, double vz) {
        Objects.requireNonNull(shooter);
        if (!Double.isFinite(x + y + z + vx + vy + vz)) throw new IllegalArgumentException("Invalid arrow");
        if (arrows.size() >= MAX_ARROWS) return false;
        arrows.add(new Arrow(nextId++, shooter, x, y, z, vx, vy, vz));
        return true;
    }

    public int size() {
        return arrows.size();
    }

    public void clear() {
        arrows.clear();
    }

    public void tick(World world, List<? extends AttackTarget> targets, double dt) {
        if (!Double.isFinite(dt) || dt <= 0 || dt > .1) throw new IllegalArgumentException("Invalid arrow tick");
        var iterator = arrows.iterator();
        while (iterator.hasNext()) {
            var arrow = iterator.next();
            arrow.previousX = arrow.x;
            arrow.previousY = arrow.y;
            arrow.previousZ = arrow.z;
            if (++arrow.ageTicks > LIFETIME_TICKS || !step(world, targets, arrow, dt)) iterator.remove();
        }
    }

    public List<ArrowRenderState> renderSnapshot() {
        return arrows.stream().map(Arrow::renderState).toList();
    }

    /** Returns false when the arrow should disappear. */
    private boolean step(World world, List<? extends AttackTarget> targets, Arrow arrow, double dt) {
        var cell = new BlockPos((int) Math.floor(arrow.x), (int) Math.floor(arrow.y), (int) Math.floor(arrow.z));
        if (!world.hasChunk(Coordinates.worldToChunk(cell))) return false;
        if (arrow.stuck) {
            if (BlockRegistry.DEFAULT.isCollidable(
                    (byte) world.getBlock(stuckIn(arrow)).orElse(BlockId.AIR))) return true;
            arrow.stuck = false;
        }
        boolean wet = world.getBlock(cell).orElse(BlockId.AIR) == BlockId.WATER;
        double keep = Math.exp(-(wet ? WATER_DRAG : AIR_DRAG) * dt);
        arrow.velocityX *= keep;
        arrow.velocityY = arrow.velocityY * keep - GRAVITY * dt;
        arrow.velocityZ *= keep;
        double dx = arrow.velocityX * dt, dy = arrow.velocityY * dt, dz = arrow.velocityZ * dt;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) return true;
        arrow.aim();
        double blockDistance = raycaster
                .cast(
                        world,
                        arrow.x,
                        arrow.y,
                        arrow.z,
                        dx,
                        dy,
                        dz,
                        length,
                        id -> BlockRegistry.DEFAULT.isCollidable((byte) id))
                .map(hit -> hit.distance())
                .orElse(Double.POSITIVE_INFINITY);
        AttackTarget struck = null;
        double struckAt = blockDistance / length;
        for (var target : targets) {
            if (target == arrow.shooter || !target.attackable()) continue;
            double t = entry(target.body(), arrow.x, arrow.y, arrow.z, dx, dy, dz);
            if (t < struckAt) {
                struck = target;
                struckAt = t;
            }
        }
        if (struck != null) {
            double speedPerMinecraftTick = Math.sqrt(arrow.velocityX * arrow.velocityX
                            + arrow.velocityY * arrow.velocityY
                            + arrow.velocityZ * arrow.velocityZ)
                    / 20;
            float damage = (float) Math.ceil(speedPerMinecraftTick * BASE_DAMAGE);
            double horizontal = Math.max(1e-9, Math.hypot(arrow.velocityX, arrow.velocityZ));
            struck.hurt(
                    DamageSource.ARROW,
                    damage,
                    arrow.velocityX / horizontal * KNOCKBACK,
                    arrow.velocityZ / horizontal * KNOCKBACK);
            return false;
        }
        if (blockDistance <= length) {
            // Stop a hair before the face so the arrow sits in the block it hit rather than beyond it.
            double travel = Math.max(0, blockDistance - .05) / length;
            arrow.x += dx * travel;
            arrow.y += dy * travel;
            arrow.z += dz * travel;
            arrow.velocityX = arrow.velocityY = arrow.velocityZ = 0;
            arrow.stuck = true;
            return true;
        }
        arrow.x += dx;
        arrow.y += dy;
        arrow.z += dz;
        return true;
    }

    private static BlockPos stuckIn(Arrow arrow) {
        return new BlockPos(
                (int) Math.floor(arrow.x + arrow.directionX * .1),
                (int) Math.floor(arrow.y + arrow.directionY * .1),
                (int) Math.floor(arrow.z + arrow.directionZ * .1));
    }

    /** Fraction of the segment at which it enters the box grown by the hit margin, or infinity when it misses. */
    static double entry(Aabb box, double x, double y, double z, double dx, double dy, double dz) {
        double near = 0, far = 1;
        double[] origin = {x, y, z}, delta = {dx, dy, dz};
        double[] min = {box.minX() - HIT_MARGIN, box.minY() - HIT_MARGIN, box.minZ() - HIT_MARGIN};
        double[] max = {box.maxX() + HIT_MARGIN, box.maxY() + HIT_MARGIN, box.maxZ() + HIT_MARGIN};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1e-12) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double t1 = (min[axis] - origin[axis]) / delta[axis], t2 = (max[axis] - origin[axis]) / delta[axis];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) return Double.POSITIVE_INFINITY;
        }
        return near;
    }
}
