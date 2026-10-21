package com.norbertfila.items;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemSystemTest {
    @Test
    void mergingAThrownStackKeepsItsLongerPickupDelay() {
        var items = restored(
                new ItemSnapshot(1, BlockId.DIRT, 63, 8.5, 1.125, 8.5, 0, 0, 0, 0, 9, true),
                new ItemSnapshot(2, BlockId.DIRT, 3, 8.5, 1.125, 8.5, 0, 0, 0, 40, 0, true));
        var world = world();
        tick(items, world);
        assertEquals(64, items.snapshot().entities().getFirst().count());
        assertEquals(39, items.snapshot().entities().getFirst().pickupDelayTicks());
        var inventory = new Inventory();
        var pickup = new Aabb(8, 1, 8, 9, 3, 9);
        for (int i = 0; i < 38; i++) items.tick(world, CENTER, 0, DT, pickup, inventory);
        assertTrue(inventory.stack(0).isEmpty());
        assertEquals(
                66,
                items.snapshot().entities().stream()
                        .mapToInt(ItemSnapshot::count)
                        .sum());
        items.tick(world, CENTER, 0, DT, pickup, inventory);
        assertTrue(items.snapshot().entities().isEmpty());
        assertEquals(64, inventory.stack(0).orElseThrow().count());
        assertEquals(2, inventory.stack(1).orElseThrow().count());
    }

    @Test
    void explicitSpawnSharesIdsWithBlockDropsAndValidatesBeforeMutatingState() {
        var items = new ItemSystem();
        var stack = new ItemStack(BlockId.DIRT, 3);
        var before = items.snapshot();
        assertThrows(IllegalArgumentException.class, () -> items.trySpawn(stack, Double.NaN, 2, 3, 1, 2, 3, 10));
        assertEquals(before, items.snapshot());
        assertTrue(items.trySpawn(stack, 1.25, 2.5, 3.75, 1, 2, 3, 10));
        items.spawn(new BlockPos(8, 2, 8), List.of(new ItemStack(BlockId.COBBLE, 1)));
        assertEquals(
                new ItemSnapshot(1, BlockId.DIRT, 3, 1.25, 2.5, 3.75, 1, 2, 3, 10, 0, false),
                items.snapshot().entities().getFirst());
        assertEquals(2, items.snapshot().entities().getLast().id());
        assertEquals(3, items.snapshot().nextId());
    }

    private static final double DT = 1 / 60d;
    private static final ChunkPos CENTER = new ChunkPos(0, 0, 0);

    @ParameterizedTest
    @CsvSource({"2,2", "3,11", "1,2", "8,8", "21,21"})
    void resolvesBlockDrops(int block, int item) {
        assertEquals(
                List.of(new ItemStack(item, 1)),
                new BlockDropResolver().getDrops(block, new ToolContext(Tool.WOODEN_PICKAXE.id())));
    }

    @Test
    void nonDroppingBlocksAndUnknownIds() {
        var resolver = new BlockDropResolver();
        for (int id : new int[] {BlockId.AIR, BlockId.WATER, BlockId.BEDROCK})
            assertTrue(resolver.getDrops(id, ToolContext.HAND).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> resolver.getDrops(256, ToolContext.HAND));
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(BlockId.DIRT, 65));
    }

    @Test
    void dropsFallLandAndFallAgainAfterSupportIsRemoved() {
        var world = world();
        var items = new ItemSystem();
        items.spawn(new BlockPos(8, 4, 8), List.of(new ItemStack(BlockId.COBBLE, 1)));
        var initial = items.snapshot();
        for (int i = 0; i < 180; i++) tick(items, world);
        var landed = items.snapshot().entities().getFirst();
        assertEquals(1.125, landed.y(), 1e-9);
        assertEquals(0, landed.vy());
        assertTrue(landed.onGround());
        assertEquals(4.5, initial.entities().getFirst().y());
        for (int x = (int) Math.floor(landed.x()) - 1; x <= (int) Math.floor(landed.x()) + 1; x++)
            for (int z = (int) Math.floor(landed.z()) - 1; z <= (int) Math.floor(landed.z()) + 1; z++)
                world.setBlock(new BlockPos(x, 0, z), BlockId.AIR, 8);
        tick(items, world);
        assertTrue(items.snapshot().entities().getFirst().y() < landed.y());
    }

    @Test
    void sweptCollisionStopsFastItemsAtWallsAndFloors() {
        var world = world();
        for (int y = 1; y < 5; y++) world.setBlock(new BlockPos(9, y, 8), BlockId.STONE, 8);
        var items = restored(new ItemSnapshot(1, BlockId.DIRT, 1, 8.5, 2, 8.5, 32, -20, 0, 0, 0, false));
        for (int i = 0; i < 60; i++) tick(items, world);
        var item = items.snapshot().entities().getFirst();
        assertTrue(item.x() <= 8.875);
        assertEquals(1.125, item.y(), 1e-9);
        assertEquals(0, item.vx());
    }

    @Test
    void pickupWaitsThenTakesOnlyAvailableCapacity() {
        var world = world();
        var items = restored(new ItemSnapshot(1, BlockId.DIRT, 10, 8.5, 1.125, 8.5, 0, 0, 0, 10, 0, true));
        byte[] blocks = new byte[37];
        int[] counts = new int[37];
        Arrays.fill(blocks, BlockId.STONE);
        Arrays.fill(counts, 64);
        blocks[0] = BlockId.DIRT;
        counts[0] = 61;
        var inventory = new Inventory(new InventorySnapshot(blocks, counts, 0));
        var box = ItemSystem.pickupBox(new Aabb(8.2, 1, 8.2, 8.8, 2.8, 8.8));
        for (int i = 0; i < 9; i++) items.tick(world, CENTER, 0, DT, box, inventory);
        assertEquals(61, inventory.snapshot().count(0));
        items.tick(world, CENTER, 0, DT, box, inventory);
        assertEquals(64, inventory.snapshot().count(0));
        assertEquals(7, items.snapshot().entities().getFirst().count());
        items.tick(world, CENTER, 0, DT, box, inventory);
        assertEquals(7, items.snapshot().entities().getFirst().count());
        for (int i = 0; i < 64; i++) inventory.consumeSelected();
        items.tick(world, CENTER, 0, DT, box, inventory);
        assertEquals(7, inventory.snapshot().count(0));
        assertTrue(items.snapshot().entities().isEmpty());
        assertEquals(64, inventory.snapshot().count(Inventory.CARRIED));
    }

    @Test
    void pickupUsesTheWholePlayerBodyAndRejectsNonIntersectingBoxes() {
        var world = world();
        var items = restored(new ItemSnapshot(1, BlockId.DIRT, 1, 8.5, 2.7, 8.5, 0, 0, 0, 0, 0, false));
        var inventory = new Inventory();
        items.tick(world, CENTER, 0, DT, new Aabb(1, 1, 1, 2, 2, 2), inventory);
        assertEquals(1, items.snapshot().entities().size());
        items.tick(world, CENTER, 0, DT, ItemSystem.pickupBox(new Aabb(8.2, 1, 8.2, 8.8, 2.8, 8.8)), inventory);
        assertTrue(items.snapshot().entities().isEmpty());
        assertEquals(1, inventory.snapshot().count(0));
    }

    @Test
    void mergingConservesCountsAtChunkBoundariesAndDoesNotRefreshLifetime() {
        var world = world();
        var items = restored(
                new ItemSnapshot(1, BlockId.DIRT, 50, 15.8, 1.125, 8.5, 0, 0, 0, 0, 9, true),
                new ItemSnapshot(2, BlockId.DIRT, 30, 16.1, 1.125, 8.5, 0, 0, 0, 5, 100, true),
                new ItemSnapshot(3, BlockId.STONE, 1, 16.2, 1.125, 8.5, 0, 0, 0, 0, 0, true));
        items.tick(world, CENTER, 1, DT, null, new Inventory());
        var state = items.snapshot().entities();
        assertEquals(64, state.get(0).count());
        assertEquals(16, state.get(1).count());
        assertEquals(101, state.get(0).ageTicks());
        assertEquals(4, state.get(0).pickupDelayTicks());
        assertEquals(81, state.stream().mapToInt(ItemSnapshot::count).sum());
    }

    @Test
    void crossingChunksUpdatesPickupIndexIncludingNegativeCoordinates() {
        var world = world();
        var items = restored(new ItemSnapshot(1, BlockId.DIRT, 1, .05, 2, 8.5, -12, 0, 0, 0, 0, false));
        items.tick(world, CENTER, 1, DT, null, new Inventory());
        assertTrue(items.snapshot().entities().getFirst().x() < 0);
        var inventory = new Inventory();
        items.tick(world, CENTER, 1, DT, new Aabb(-1, 1, 8, 0, 3, 9), inventory);
        assertTrue(items.snapshot().entities().isEmpty());
        assertEquals(1, inventory.snapshot().count(0));
        items.spawn(new BlockPos(-1, 2, 8), List.of(new ItemStack(BlockId.DIRT, 1)));
        assertEquals(1, items.snapshot().entities().size());
    }

    @Test
    void missingChunksAndSimulationAreaFreezeItemsWithoutLosingThem() {
        var world = new World();
        var items = restored(new ItemSnapshot(1, BlockId.DIRT, 1, 8.5, 2, 8.5, 1, 1, 1, 10, 0, false));
        var initial = items.snapshot();
        tick(items, world);
        assertEquals(initial, items.snapshot());
        world = world();
        items.tick(world, new ChunkPos(5, 0, 0), 0, DT, null, new Inventory());
        assertEquals(initial, items.snapshot());
        tick(items, world);
        assertNotEquals(initial, items.snapshot());
        var beforeUnload = items.snapshot();
        var chunk = world.removeChunk(CENTER);
        tick(items, world);
        assertEquals(beforeUnload, items.snapshot());
        assertTrue(items.renderSnapshot(world).isEmpty());
        world.putChunk(CENTER, chunk);
        tick(items, world);
        assertTrue(items.snapshot().entities().getFirst().ageTicks()
                > beforeUnload.entities().getFirst().ageTicks());
        items.pause();
        var rendering = items.renderSnapshot(world).getFirst();
        assertFalse(rendering.moving());
        assertEquals(rendering.item().x(), rendering.previousX());
    }

    @Test
    void expiresOnlyAfterTheRemainingActiveTicksAndKeepsIdsAfterRestore() {
        var items = restored(
                new ItemSnapshot(7, BlockId.DIRT, 1, 8.5, 1.125, 8.5, 0, 0, 0, 0, ItemSystem.DESPAWN_TICKS - 2, true));
        var world = world();
        tick(items, world);
        var restarted = new ItemSystem(items.snapshot());
        tick(restarted, world);
        assertTrue(restarted.snapshot().entities().isEmpty());
        restarted.spawn(new BlockPos(8, 2, 8), List.of(new ItemStack(BlockId.DIRT, 1)));
        assertEquals(8, restarted.snapshot().entities().getFirst().id());
    }

    @Test
    void placedBlockPushesAnItemOutInsteadOfEmbeddingIt() {
        var world = world();
        world.setBlock(new BlockPos(8, 1, 8), BlockId.STONE, 8);
        var items = restored(new ItemSnapshot(1, BlockId.DIRT, 1, 8.5, 1.5, 8.5, 0, 0, 0, 0, 0, false));
        tick(items, world);
        var item = new ItemEntity(items.snapshot().entities().getFirst());
        assertTrue(new VoxelCollision().spaceFree(world, item.body()));
    }

    @Test
    void validatesUniqueIdsAndCapacityBeforeSpawning() {
        var item = new ItemSnapshot(1, BlockId.DIRT, 1, 8.5, 2, 8.5, 0, 0, 0, 0, 0, false);
        assertThrows(IllegalArgumentException.class, () -> new ItemState(2, List.of(item, item)));
        assertThrows(IllegalArgumentException.class, () -> new ItemState(1, List.of(item)));
        var items = new ItemSystem();
        var block = new BlockPos(8, 2, 8);
        for (int i = 0; i < ItemState.MAX_ENTITIES; i++) items.spawn(block, List.of(new ItemStack(BlockId.DIRT, 1)));
        var full = items.snapshot();
        assertFalse(items.canSpawn(block, List.of(new ItemStack(BlockId.DIRT, 1))));
        assertThrows(IllegalStateException.class, () -> items.spawn(block, List.of(new ItemStack(BlockId.DIRT, 1))));
        assertEquals(full, items.snapshot());
        var world = world();
        world.setBlock(block, BlockId.DIRT, 8);
        var interaction = new BlockInteraction(world, 8);
        assertTrue(interaction
                .remove(PlayerController.Mode.SURVIVAL, 8.5, 2.5, 6, 0, 0, 1, items)
                .isEmpty());
        assertEquals(BlockId.DIRT, world.getBlock(block).orElseThrow());
        assertEquals(full, items.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> full.entities().clear());
    }

    private static ItemSystem restored(ItemSnapshot... items) {
        return new ItemSystem(new ItemState(
                Arrays.stream(items).mapToLong(ItemSnapshot::id).max().orElse(0) + 1, List.of(items)));
    }

    private static void tick(ItemSystem items, World world) {
        items.tick(world, CENTER, 0, DT, null, new Inventory());
    }

    private static World world() {
        var world = new World();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                var chunk = new Chunk();
                for (int bx = 0; bx < Chunk.SIZE; bx++)
                    for (int bz = 0; bz < Chunk.SIZE; bz++) chunk.set(bx, 0, bz, BlockId.STONE);
                world.putChunk(new ChunkPos(x, 0, z), chunk);
            }
        return world;
    }
}
