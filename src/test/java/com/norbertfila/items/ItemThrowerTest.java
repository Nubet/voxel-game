package com.norbertfila.items;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.*;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemThrowerTest {
    private static final PlayerState.Position EYE = new PlayerState.Position(8.5, 3, 8.5);

    @ParameterizedTest
    @CsvSource({"0,0,1,0,0", "90,0,0,0,1", "180,0,-1,0,0", "-90,0,0,0,-1", "0,90,0,1,0", "0,-90,0,-1,0"})
    void throwsNearTheEyeInTheLookDirection(double yaw, double pitch, double dx, double dy, double dz) {
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 8);
        var items = new ItemSystem();
        var thrower = thrower(world(), inventory, items);
        assertTrue(thrower.drop(0, false, EYE, yaw, pitch));
        var item = items.snapshot().entities().getFirst();
        assertEquals(EYE.x() + .3 * dx, item.x(), 1e-9);
        assertEquals(EYE.y() + .3 * dy, item.y(), 1e-9);
        assertEquals(EYE.z() + .3 * dz, item.z(), 1e-9);
        assertEquals(4 * dx, item.vx(), .1);
        assertEquals(4 * dy, item.vy(), .1);
        assertEquals(4 * dz, item.vz(), .1);
        assertEquals(1, item.count());
        assertEquals(40, item.pickupDelayTicks());
        assertEquals(7, inventory.stack(0).orElseThrow().count());
        assertTrue(thrower.drop(0, true, EYE, yaw, pitch));
        assertEquals(7, items.snapshot().entities().getLast().count());
        assertTrue(inventory.stack(0).isEmpty());
        var after = items.snapshot();
        assertFalse(thrower.drop(0, false, EYE, yaw, pitch));
        assertEquals(after, items.snapshot());
    }

    @Test
    void supportsCursorStacksAndUsesIndependentSmallRandomness() {
        var inventory = new Inventory();
        inventory.insert(BlockId.POPPY, 3);
        inventory.click(0, false, false);
        var items = new ItemSystem();
        var thrower = thrower(world(), inventory, items);
        assertTrue(thrower.drop(Inventory.CARRIED, false, EYE, 0, 0));
        assertTrue(thrower.drop(Inventory.CARRIED, false, EYE, 0, 0));
        var entities = items.snapshot().entities();
        assertNotEquals(entities.getFirst().vx(), entities.getLast().vx());
        assertEquals(1, inventory.stack(Inventory.CARRIED).orElseThrow().count());
    }

    @Test
    void clipsSpawnAgainstAWallAndCannotThrowThroughIt() {
        var world = world();
        world.setBlock(new BlockPos(9, 3, 8), BlockId.STONE, 8);
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        var items = new ItemSystem();
        assertTrue(thrower(world, inventory, items).drop(0, false, new PlayerState.Position(8.7, 3.5, 8.5), 0, 0));
        var item = items.snapshot().entities().getFirst();
        assertEquals(8.875, item.x(), 1e-9);
        for (int i = 0; i < 5; i++) items.tick(world, new ChunkPos(0, 0, 0), 0, 1 / 60d, null, inventory);
        assertEquals(8.875, items.snapshot().entities().getFirst().x(), 1e-9);
        assertEquals(0, items.snapshot().entities().getFirst().vx());
    }

    @Test
    void unavailableOrObstructedSpawnNeverRemovesInventoryItems() {
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 8);
        var items = new ItemSystem();
        var before = inventory.snapshot();
        assertFalse(thrower(new World(), inventory, items).drop(0, true, EYE, 0, 0));
        var world = world();
        world.setBlock(new BlockPos(8, 3, 8), BlockId.STONE, 8);
        assertFalse(thrower(world, inventory, items).drop(0, true, EYE, 0, 0));
        assertEquals(before, inventory.snapshot());
        assertEquals(ItemState.EMPTY, items.snapshot());
    }

    @Test
    void throwingAtTheWorldBoundaryDoesNotLoseItemsOrCreateInvalidCoordinates() {
        var world = new World();
        world.putChunk(new ChunkPos(Math.floorDiv(Integer.MAX_VALUE, Chunk.SIZE), 0, 0), new Chunk());
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        var items = new ItemSystem();
        assertFalse(thrower(world, inventory, items)
                .drop(0, false, new PlayerState.Position(Integer.MAX_VALUE - .5, 3, 8.5), 0, 0));
        assertEquals(1, inventory.stack(0).orElseThrow().count());
        assertEquals(ItemState.EMPTY, items.snapshot());
    }

    @Test
    void crossingAnUnloadedChunkBoundaryCannotConsumeTheStack() {
        var world = world();
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 2);
        var items = new ItemSystem();
        var thrower = thrower(world, inventory, items);
        var eye = new PlayerState.Position(15.8, 3, 8.5);
        assertFalse(thrower.drop(0, false, eye, 0, 0));
        assertEquals(2, inventory.stack(0).orElseThrow().count());
        assertEquals(ItemState.EMPTY, items.snapshot());
        world.putChunk(new ChunkPos(1, 0, 0), new Chunk());
        assertTrue(thrower.drop(0, false, eye, 0, 0));
        assertEquals(16.1, items.snapshot().entities().getFirst().x(), 1e-9);
        assertEquals(1, inventory.stack(0).orElseThrow().count());
    }

    @Test
    void reachingTheEntityLimitDoesNotLoseTheStack() {
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 8);
        var items = new ItemSystem();
        for (int i = 0; i < ItemState.MAX_ENTITIES; i++)
            items.spawn(new BlockPos(8, 2, 8), List.of(new ItemStack(BlockId.DIRT, 1)));
        var before = inventory.snapshot();
        var entities = items.snapshot();
        assertFalse(thrower(world(), inventory, items).drop(0, true, EYE, 0, 0));
        assertEquals(before, inventory.snapshot());
        assertEquals(entities, items.snapshot());
    }

    @Test
    void thrownItemsUseExistingGravityAndLandWithoutImmediatePickup() {
        var world = world();
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        var items = new ItemSystem();
        var eye = new PlayerState.Position(8.5, 2.62, 8.5);
        assertTrue(thrower(world, inventory, items).drop(0, false, eye, 0, 30));
        var pickup = ItemSystem.pickupBox(
                PlayerState.at(new PlayerState.Position(8.5, 1, 8.5)).body());
        for (int i = 0; i < 39; i++) items.tick(world, new ChunkPos(0, 0, 0), 0, 1 / 60d, pickup, inventory);
        assertEquals(1, items.snapshot().entities().size());
        assertTrue(inventory.stack(0).isEmpty());
        for (int i = 0; i < 80; i++) items.tick(world, new ChunkPos(0, 0, 0), 0, 1 / 60d, null, inventory);
        var landed = items.snapshot().entities().getFirst();
        assertEquals(1.125, landed.y(), 1e-9);
        assertTrue(landed.onGround());
        assertEquals(0, landed.vy());
    }

    private static ItemThrower thrower(World world, Inventory inventory, ItemSystem items) {
        return new ItemThrower(world, inventory, items, new SplittableRandom(42));
    }

    private static World world() {
        var world = new World();
        var chunk = new Chunk();
        for (int x = 0; x < Chunk.SIZE; x++) for (int z = 0; z < Chunk.SIZE; z++) chunk.set(x, 0, z, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        return world;
    }
}
