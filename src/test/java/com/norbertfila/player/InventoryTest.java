package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.Tool;
import com.norbertfila.world.BlockId;
import java.util.Random;
import org.junit.jupiter.api.Test;

class InventoryTest {
    @Test
    void toolsNeverStackAndAreNotPlaceable() {
        var inventory = new Inventory();
        byte pickaxe = Tool.IRON_PICKAXE.id();
        assertEquals(0, inventory.insert(Byte.toUnsignedInt(pickaxe), 2));
        assertEquals(1, inventory.snapshot().count(0));
        assertEquals(1, inventory.snapshot().count(1));
        assertEquals(pickaxe, inventory.selectedItem());
        assertEquals(BlockId.AIR, inventory.selectedBlock());
        inventory.click(0, false, false);
        inventory.click(1, false, false);
        assertEquals(pickaxe, inventory.snapshot().block(1));
        assertEquals(1, inventory.snapshot().count(1));
        assertEquals(1, inventory.snapshot().count(Inventory.CARRIED));
        inventory.creativePick(2, Tool.DIAMOND_SWORD.id());
        assertEquals(1, inventory.snapshot().count(2));
        assertEquals(inventory.snapshot(), new Inventory(inventory.snapshot()).snapshot());
    }

    @Test
    void extractsExactCountsFromStorageHotbarAndCursorWithoutChangingOtherSlots() {
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 70);
        inventory.click(0, false, true);
        assertEquals(64, inventory.stack(9).orElseThrow().count());
        inventory.remove(9, 1);
        assertEquals(63, inventory.stack(9).orElseThrow().count());
        inventory.click(1, false, false);
        inventory.remove(Inventory.CARRIED, 6);
        assertTrue(inventory.stack(Inventory.CARRIED).isEmpty());
        assertEquals(BlockId.AIR, inventory.snapshot().block(Inventory.CARRIED));
        inventory.remove(9, 63);
        assertTrue(inventory.stack(9).isEmpty());
        assertEquals(BlockId.AIR, inventory.snapshot().block(9));
        var empty = inventory.snapshot();
        assertThrows(IllegalArgumentException.class, () -> inventory.remove(9, 1));
        assertThrows(IllegalArgumentException.class, () -> inventory.remove(0, 0));
        assertThrows(IllegalArgumentException.class, () -> inventory.stack(37));
        assertEquals(empty, inventory.snapshot());
    }

    @Test
    void bulkInsertFillsExistingStacksBeforeEmptySlotsAndReturnsRemainder() {
        var inventory = new Inventory();
        assertEquals(0, inventory.insert(BlockId.DIRT, 63));
        assertEquals(0, inventory.insert(BlockId.STONE, 64));
        inventory.click(1, false, false);
        assertEquals(0, inventory.insert(BlockId.DIRT, 3));
        assertEquals(64, inventory.snapshot().count(0));
        assertEquals(2, inventory.snapshot().count(1));
        assertEquals(64, inventory.snapshot().count(Inventory.CARRIED));
        assertEquals(2, inventory.insert(BlockId.STONE, 34 * 64 + 2));
        assertEquals(0, inventory.insert(BlockId.DIRT, 62));
        assertEquals(1, inventory.insert(BlockId.DIRT, 1));
        assertThrows(IllegalArgumentException.class, () -> inventory.insert(256 + BlockId.DIRT, 1));
        assertThrows(IllegalArgumentException.class, () -> inventory.insert(BlockId.DIRT, -1));
    }

    @Test
    void splitsPlacesMergesAndSwapsWithoutLosingItems() {
        Inventory inventory = new Inventory();
        for (int i = 0; i < 5; i++) assertTrue(inventory.add(BlockId.DIRT));
        inventory.click(0, true, false);
        assertEquals(2, inventory.snapshot().count(0));
        assertEquals(3, inventory.snapshot().count(36));
        inventory.click(1, true, false);
        assertEquals(1, inventory.snapshot().count(1));
        inventory.click(0, false, false);
        assertEquals(4, inventory.snapshot().count(0));
        assertEquals(0, inventory.snapshot().count(36));
        inventory.add(BlockId.STONE);
        inventory.click(0, false, false);
        inventory.click(2, false, false);
        assertEquals(BlockId.DIRT, inventory.snapshot().block(2));
        assertEquals(BlockId.STONE, inventory.snapshot().block(36));
        inventory.returnCarried();
        assertEquals(0, inventory.snapshot().count(36));
        assertEquals(6, total(inventory.snapshot()));
    }

    @Test
    void quickMoveMergesBeforeUsingEmptySlotsAndKeepsRemainder() {
        Inventory inventory = new Inventory();
        inventory.creativePick(0, BlockId.DIRT);
        inventory.click(0, false, true);
        assertEquals(64, inventory.snapshot().count(9));
        inventory.creativePick(0, BlockId.DIRT);
        inventory.click(9, true, false);
        inventory.click(10, false, false);
        inventory.click(0, false, true);
        assertEquals(64, inventory.snapshot().count(9));
        assertEquals(64, inventory.snapshot().count(10));
        inventory.click(9, false, true);
        assertEquals(64, inventory.snapshot().count(0));
        assertEquals(128, total(inventory.snapshot()));
    }

    @Test
    void fullInventoryPreservesCarriedStackAndRejectsNewItems() {
        byte[] blocks = new byte[37];
        int[] counts = new int[37];
        java.util.Arrays.fill(blocks, BlockId.STONE);
        java.util.Arrays.fill(counts, 64);
        Inventory inventory = new Inventory(new InventorySnapshot(blocks, counts, 8));
        var before = inventory.snapshot();
        assertFalse(inventory.add(BlockId.DIRT));
        inventory.click(8, false, true);
        inventory.returnCarried();
        assertEquals(before, inventory.snapshot());
        inventory.consumeSelected();
        inventory.returnCarried();
        assertEquals(64, inventory.snapshot().count(8));
        assertEquals(63, inventory.snapshot().count(36));
    }

    @Test
    void randomTransfersConserveCountsAndSnapshotIsDetached() {
        Inventory inventory = new Inventory();
        for (int i = 0; i < 180; i++) inventory.add(i % 2 == 0 ? BlockId.DIRT : BlockId.STONE);
        var initial = inventory.snapshot();
        byte[] exported = initial.blocks();
        exported[0] = BlockId.AIR;
        int[] counts = initial.counts();
        counts[0] = 0;
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            inventory.click(random.nextInt(36), random.nextBoolean(), random.nextBoolean());
            if (i % 7 == 0) inventory.returnCarried();
            var snapshot = inventory.snapshot();
            assertEquals(180, total(snapshot));
            assertEquals(90, total(snapshot, BlockId.DIRT));
            assertEquals(90, total(snapshot, BlockId.STONE));
        }
        assertEquals(180, total(initial));
        assertEquals(BlockId.DIRT, initial.block(0));
        assertThrows(IllegalArgumentException.class, () -> inventory.click(36, false, false));
        assertThrows(IllegalArgumentException.class, () -> inventory.select(9));
        assertThrows(IllegalArgumentException.class, () -> inventory.creativePick(0, BlockId.AIR));
        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(new byte[37], new int[37], 9));
        counts = new int[37];
        counts[0] = 1;
        int[] invalid = counts;
        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(new byte[37], invalid, 0));
    }

    private static int total(InventorySnapshot snapshot) {
        return java.util.Arrays.stream(snapshot.counts()).sum();
    }

    private static int total(InventorySnapshot snapshot, byte block) {
        int count = 0;
        for (int i = 0; i < 37; i++) if (snapshot.block(i) == block) count += snapshot.count(i);
        return count;
    }
}
