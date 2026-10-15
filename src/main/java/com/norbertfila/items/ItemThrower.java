package com.norbertfila.items;

import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.VoxelCollision;
import com.norbertfila.world.World;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

public final class ItemThrower {
    private static final double SPAWN_OFFSET = .3;
    private static final double SPEED = 4;
    private final World world;
    private final Inventory inventory;
    private final ItemSystem items;
    private final RandomGenerator random;
    private final VoxelCollision collision = new VoxelCollision();

    public ItemThrower(World world, Inventory inventory, ItemSystem items) {
        this(world, inventory, items, new SplittableRandom());
    }

    ItemThrower(World world, Inventory inventory, ItemSystem items, RandomGenerator random) {
        this.world = Objects.requireNonNull(world);
        this.inventory = Objects.requireNonNull(inventory);
        this.items = Objects.requireNonNull(items);
        this.random = Objects.requireNonNull(random);
    }

    public boolean drop(int slot, boolean wholeStack, PlayerState.Position eye, double yaw, double pitch) {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || pitch < -90 || pitch > 90)
            throw new IllegalArgumentException("Invalid throw direction");
        var source = inventory.stack(slot);
        if (source.isEmpty()) return false;
        if (!withinWorld(eye.x()) || !withinWorld(eye.y()) || !withinWorld(eye.z())) return false;
        var stack = source.orElseThrow();
        var thrown = new ItemStack(stack.itemId(), wholeStack ? stack.count() : 1);
        double yawRadians = Math.toRadians(yaw), pitchRadians = Math.toRadians(pitch);
        double dx = Math.cos(yawRadians) * Math.cos(pitchRadians),
                dy = Math.sin(pitchRadians),
                dz = Math.sin(yawRadians) * Math.cos(pitchRadians);
        double half = ItemEntity.HALF_SIZE;
        var body = new Aabb(
                eye.x() - half, eye.y() - half, eye.z() - half, eye.x() + half, eye.y() + half, eye.z() + half);
        if (!collision.spaceFree(world, body)) return false;
        var spawn = collision.move(world, body, dx * SPAWN_OFFSET, dy * SPAWN_OFFSET, dz * SPAWN_OFFSET);
        if (!spawn.available()) return false;
        var position = spawn.body();
        if (!items.trySpawn(
                thrown,
                (position.minX() + position.maxX()) * .5,
                (position.minY() + position.maxY()) * .5,
                (position.minZ() + position.maxZ()) * .5,
                dx * SPEED + random.nextDouble(-.1, .1),
                dy * SPEED + random.nextDouble(-.1, .1),
                dz * SPEED + random.nextDouble(-.1, .1),
                ItemSystem.THROW_PICKUP_DELAY_TICKS)) return false;
        // No snapshot is published between spawning and extraction on the simulation owner thread.
        inventory.remove(slot, thrown.count());
        return true;
    }

    private static boolean withinWorld(double coordinate) {
        return coordinate >= Integer.MIN_VALUE + 2d && coordinate <= Integer.MAX_VALUE - 2d;
    }
}
