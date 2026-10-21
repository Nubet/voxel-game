package com.norbertfila.explosion;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.interaction.VoxelRaycaster;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.World;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Minecraft's explosion: rays from a 16x16x16 grid lose strength in every block they cross and destroy the blocks
 * they still have strength for; targets inside twice the power take damage scaled by distance and by how much of
 * their body the blast can see, and are pushed away.
 */
public final class Explosion {
    private static final int GRID = 16;
    private static final double STEP = .3;
    // Push in blocks per second at full impact; Minecraft adds about one block per tick.
    private static final double KNOCKBACK = 16;
    // Rays stop just short of the center, because a blast resting on a floor would otherwise hit that floor's top face.
    private static final double CENTER_CLEARANCE = 1e-3;
    private final World world;
    private final BlockSink blocks;
    private final VoxelRaycaster raycaster = new VoxelRaycaster();

    public Explosion(World world, BlockSink blocks) {
        this.world = Objects.requireNonNull(world);
        this.blocks = Objects.requireNonNull(blocks);
    }

    public record Result(List<BlockPos> destroyed, int targetsHit) {}

    public Result explode(
            double x, double y, double z, float power, List<? extends AttackTarget> targets, RandomGenerator random) {
        if (!(power > 0)) throw new IllegalArgumentException("Explosion power must be positive");
        int hit = 0;
        for (var target : targets) if (damage(target, x, y, z, power)) hit++;
        var destroyed = affectedBlocks(x, y, z, power, random);
        if (!destroyed.isEmpty()) blocks.destroy(destroyed);
        return new Result(destroyed, hit);
    }

    List<BlockPos> affectedBlocks(double x, double y, double z, float power, RandomGenerator random) {
        var affected = new LinkedHashSet<BlockPos>();
        for (int i = 0; i < GRID; i++)
            for (int j = 0; j < GRID; j++)
                for (int k = 0; k < GRID; k++) {
                    if (i != 0 && i != GRID - 1 && j != 0 && j != GRID - 1 && k != 0 && k != GRID - 1) continue;
                    double dx = i / (GRID - 1.0) * 2 - 1, dy = j / (GRID - 1.0) * 2 - 1, dz = k / (GRID - 1.0) * 2 - 1;
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= length;
                    dy /= length;
                    dz /= length;
                    double strength = power * (.7 + random.nextDouble() * .6);
                    double px = x, py = y, pz = z;
                    while (strength > 0) {
                        var position = new BlockPos((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
                        var block = world.getBlock(position);
                        if (block.isEmpty()) break;
                        int id = block.getAsInt();
                        if (id != BlockId.AIR) strength -= (ExplosionResistance.of((byte) id) + .3) * .3;
                        if (strength > 0 && id != BlockId.AIR && ExplosionResistance.of((byte) id) < 100)
                            affected.add(position);
                        px += dx * STEP;
                        py += dy * STEP;
                        pz += dz * STEP;
                        strength -= STEP * .75;
                    }
                }
        return new ArrayList<>(affected);
    }

    private boolean damage(AttackTarget target, double x, double y, double z, float power) {
        double radius = power * 2;
        double dx = target.x() - x, dy = target.eyeY() - y, dz = target.z() - z;
        double distance = Math.sqrt(dx * dx + (target.y() - y) * (target.y() - y) + dz * dz) / radius;
        if (distance > 1) return false;
        double impact = (1 - distance) * exposure(x, y, z, target.body());
        if (impact <= 0) return false;
        float amount = (float) Math.floor((impact * impact + impact) / 2 * 7 * radius + 1);
        double length = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
        return target.hurt(
                DamageSource.EXPLOSION, amount, dx / length * impact * KNOCKBACK, dz / length * impact * KNOCKBACK);
    }

    /** Fraction of sample points on the body that the blast center can see, as in Minecraft's getSeenPercent. */
    double exposure(double x, double y, double z, Aabb body) {
        double sizeX = body.maxX() - body.minX(), sizeY = body.maxY() - body.minY(), sizeZ = body.maxZ() - body.minZ();
        double stepX = 1 / (sizeX * 2 + 1), stepY = 1 / (sizeY * 2 + 1), stepZ = 1 / (sizeZ * 2 + 1);
        int seen = 0, total = 0;
        for (double fx = 0; fx <= 1; fx += stepX)
            for (double fy = 0; fy <= 1; fy += stepY)
                for (double fz = 0; fz <= 1; fz += stepZ) {
                    double px = body.minX() + sizeX * fx, py = body.minY() + sizeY * fy, pz = body.minZ() + sizeZ * fz;
                    double ddx = x - px, ddy = y - py, ddz = z - pz;
                    double length = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    total++;
                    if (length <= CENTER_CLEARANCE
                            || raycaster
                                    .cast(
                                            world,
                                            px,
                                            py,
                                            pz,
                                            ddx,
                                            ddy,
                                            ddz,
                                            length - CENTER_CLEARANCE,
                                            id -> BlockRegistry.DEFAULT.isCollidable((byte) id))
                                    .isEmpty()) seen++;
                }
        return total == 0 ? 0 : seen / (double) total;
    }
}
