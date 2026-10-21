package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.ItemId;
import com.norbertfila.player.*;
import com.norbertfila.ui.*;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class InventoryScreenTest {
    private static final UiViewport VIEWPORT = new UiViewport(1280, 720, 1280, 720);

    @Test
    void dropKeysMapStorageAndHotbarSlotsAndNeverTreatTheCreativeCatalogAsAStack() {
        for (boolean creative : new boolean[] {false, true}) {
            var screen = new InventoryScreen(creative);
            screen.update(InventorySnapshot.EMPTY, 8, VIEWPORT);
            int catalogCells = 27;
            for (int cell = 0; cell < catalogCells + 9; cell++) {
                var box = screen.bounds(cell);
                screen.updatePointer(false, false, box.x() + 1, box.y() + 1);
                int expected = creative && cell < catalogCells
                        ? Inventory.CARRIED
                        : cell >= catalogCells ? cell - catalogCells : cell + 9;
                assertEquals(new Screen.Action.DropSlot(expected, false), screen.keyPress(UiKey.DROP_ONE));
                assertEquals(new Screen.Action.DropSlot(expected, true), screen.keyPress(UiKey.DROP_STACK));
            }
            screen.updatePointer(false, false, -1, -1);
            assertEquals(new Screen.Action.DropSlot(Inventory.CARRIED, false), screen.keyPress(UiKey.DROP_ONE));
        }
    }

    @Test
    void dropTargetsKeyboardFocusUntilThePointerMovesAndCancelsStaleTargets() {
        var screen = new InventoryScreen(false);
        screen.update(InventorySnapshot.EMPTY, 0, VIEWPORT);
        var box = screen.bounds(5);
        screen.updatePointer(false, false, box.x() + 1, box.y() + 1);
        screen.keyPress(UiKey.NEXT);
        screen.updatePointer(false, false, box.x() + 1, box.y() + 1);
        assertEquals(new Screen.Action.DropSlot(10, false), screen.keyPress(UiKey.DROP_ONE));
        screen.updatePointer(false, false, box.x() + 2, box.y() + 1);
        assertEquals(new Screen.Action.DropSlot(14, false), screen.keyPress(UiKey.DROP_ONE));
        screen.cancelInteraction();
        assertEquals(new Screen.Action.DropSlot(Inventory.CARRIED, false), screen.keyPress(UiKey.DROP_ONE));
    }

    @Test
    void survivalMapsAllSlotsAndConsumesCloseFrame() {
        var screen = new InventoryScreen(false);
        screen.update(InventorySnapshot.EMPTY, 0, VIEWPORT);
        var manager = new ScreenManager();
        manager.open(screen);
        var box = screen.bounds(0);
        manager.updatePointer(new Screen.Pointer(true, true, false, false, false, box.x() + 1, box.y() + 1));
        assertNull(manager.pollAction());
        manager.updatePointer(new Screen.Pointer(false, false, false, false, false, 0, 0));
        for (int cell = 0; cell < 36; cell++) {
            box = screen.bounds(cell);
            manager.updatePointer(new Screen.Pointer(false, false, true, true, true, box.x() + 1, box.y() + 1));
            assertEquals(
                    new Screen.Action.SlotClick(cell >= 27 ? cell - 27 : cell + 9, true, true), manager.pollAction());
        }
        manager.keyPress(UiKey.BACK);
        assertTrue(manager.blocksGameplay());
        assertFalse(manager.isOpen());
        manager.beginFrame();
        assertFalse(manager.blocksGameplay());
    }

    @Test
    void creativeCatalogIncludesMaterialsButHidesPlantStatesAndTargetsSelectedHotbarSlot() {
        var screen = new InventoryScreen(true);
        screen.update(InventorySnapshot.EMPTY, 8, VIEWPORT);
        screen.updatePointer(false, false, 0, 0);
        var catalog = InventoryScreen.catalogBlocks();
        var hidden = java.util.Set.of(
                BlockId.VINE_WEST,
                BlockId.VINE_SOUTH,
                BlockId.VINE_NORTH,
                BlockId.COCOA_WEST,
                BlockId.COCOA_SOUTH,
                BlockId.COCOA_NORTH,
                BlockId.TALL_GRASS_TOP,
                BlockId.LARGE_FERN_TOP);
        for (int id = 1; id < 256; id++)
            assertEquals(ItemId.isValid((byte) id) && !hidden.contains((byte) id), catalog.contains((byte) id));
        for (int i = 0; i < catalog.size(); i++) {
            if (i > 0 && i % 27 == 0) screen.keyPress(UiKey.PAGE_DOWN);
            var box = screen.bounds(i % 27);
            assertEquals(
                    new Screen.Action.CreativePick(catalog.get(i), 8),
                    screen.updatePointer(true, true, box.x() + 1, box.y() + 1));
        }
        screen.cancelInteraction();
        assertEquals(
                Screen.Action.NONE,
                screen.updatePointer(
                        true, true, screen.bounds(0).x(), screen.bounds(0).y()));
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
    }

    @Test
    void pageButtonsAndKeysReachTheLastPageWithoutMovingTheHotbarOffscreen() {
        var screen = new InventoryScreen(true);
        var viewport = new UiViewport(640, 360, 640, 360);
        screen.update(InventorySnapshot.EMPTY, 8, viewport);
        screen.updatePointer(false, false, 0, 0);
        var first = screen.bounds(0);
        var next = screen.pageBounds(true);
        screen.updatePointer(true, true, next.x() + 1, next.y() + 1);
        assertEquals(
                new Screen.Action.CreativePick(InventoryScreen.catalogBlocks().get(27), 8),
                screen.updatePointer(true, true, first.x() + 1, first.y() + 1));
        screen.keyPress(UiKey.PAGE_UP);
        screen.keyPress(UiKey.PAGE_UP);
        assertEquals(
                new Screen.Action.CreativePick(InventoryScreen.catalogBlocks().getFirst(), 8),
                screen.keyPress(UiKey.ACTIVATE));
        for (int i = 0; i < 20; i++) screen.keyPress(UiKey.PAGE_DOWN);
        int lastPage = (InventoryScreen.catalogBlocks().size() - 1) / 27;
        int used = InventoryScreen.catalogBlocks().size() - lastPage * 27;
        var empty = screen.bounds(used);
        assertEquals(Screen.Action.NONE, screen.updatePointer(true, true, empty.x() + 1, empty.y() + 1));
        var hotbar = screen.bounds(35);
        assertTrue(hotbar.y() + hotbar.height() <= viewport.height());
        assertEquals(
                new Screen.Action.SlotClick(8, false, false),
                screen.updatePointer(true, true, hotbar.x() + 1, hotbar.y() + 1));
    }

    @Test
    void renderHandlesAllBlocksCountsAndCarriedStackAtScaledCoordinates() {
        var screen = new InventoryScreen(true);
        var inventory = new Inventory();
        inventory.creativePick(0, BlockId.POPPY);
        inventory.click(0, true, false);
        screen.update(inventory.snapshot(), 0, VIEWPORT);
        screen.updatePointer(false, false, 200, 100);
        var canvas = new UiCanvas();
        screen.render(canvas, VIEWPORT);
        assertTrue(canvas.floatCount() > 0);
        for (int cell = 0; cell < 36; cell++) {
            var box = screen.bounds(cell);
            assertTrue(box.x() >= 0 && box.x() + box.width() <= VIEWPORT.width());
            assertTrue(box.y() >= 0 && box.y() + box.height() <= VIEWPORT.height());
        }
        assertEquals(Screen.Action.NONE, screen.keyPress(UiKey.NEXT));
        assertInstanceOf(Screen.Action.CreativePick.class, screen.keyPress(UiKey.ACTIVATE));
    }
}
