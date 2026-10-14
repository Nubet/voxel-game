package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.persistence.world.WorldEntry;
import com.norbertfila.ui.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldSelectionScreenTest {
    private static final UiViewport MINIMUM = new UiViewport(320, 240, 320, 240);

    @Test
    void sortsImmutableRowsByCommittedDateThenUuidAndDisablesInvalidWorlds() {
        var input = new ArrayList<>(List.of(
                WorldUiFixtures.entry(3, 10, WorldEntry.Status.READY),
                WorldUiFixtures.entry(2, 20, WorldEntry.Status.CORRUPTED),
                WorldUiFixtures.entry(1, 20, WorldEntry.Status.READY)));
        var list = new WorldSelectionScreen(input);
        input.clear();
        assertEquals(
                List.of(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3)),
                list.entries().stream().map(WorldEntry::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> list.entries().clear());
        list.keyPress(UiKey.DOWN);
        assertFalse(list.playButton().enabled());
        assertEquals(Screen.Action.NONE, list.keyPress(UiKey.ACTIVATE));
        list.keyPress(UiKey.NEXT);
        assertTrue(list.createButton().focused());
        assertInstanceOf(Screen.Action.ShowCreateWorld.class, list.keyPress(UiKey.ACTIVATE));
        assertEquals(Screen.Action.NONE, list.keyPress(UiKey.ACTIVATE));
    }

    @Test
    void emptyListSupportsCreateAndBackButCannotPlay() {
        var list = new WorldSelectionScreen(List.of());
        list.layout(MINIMUM);
        assertTrue(list.selectedId().isEmpty());
        assertFalse(list.playButton().enabled());
        assertEquals(Screen.Action.NONE, list.keyPress(UiKey.ACTIVATE));
        assertEquals(Screen.Action.CLOSE, list.keyPress(UiKey.BACK));
        list.keyPress(UiKey.NEXT);
        assertInstanceOf(Screen.Action.ShowCreateWorld.class, list.keyPress(UiKey.ACTIVATE));
    }

    @Test
    void wheelKeyboardAndDragStayBoundedAcrossAThousandRows() {
        var list = new WorldSelectionScreen(WorldUiFixtures.entries(1000));
        list.layout(MINIMUM);
        var bounds = list.listBounds();
        list.scroll(-.2, bounds.x() + 2, bounds.y() + 2);
        assertEquals(0, list.firstVisible());
        list.scroll(-.2, bounds.x() + 2, bounds.y() + 2);
        assertEquals(1, list.firstVisible());
        list.scroll(-100, -1, -1);
        assertEquals(1, list.firstVisible());
        list.keyPress(UiKey.END);
        assertEquals(1000 - list.visibleRows(), list.firstVisible());
        list.keyPress(UiKey.HOME);
        assertEquals(0, list.firstVisible());
        list.keyPress(UiKey.PAGE_DOWN);
        assertTrue(list.firstVisible() > 0);
        list.updatePointer(false, false, 0, 0);
        list.updatePointer(true, true, bounds.x() + bounds.width() - 2, bounds.y() + bounds.height() - 1);
        list.updatePointer(false, false, bounds.x() + bounds.width() - 2, bounds.y() + bounds.height() - 1);
        assertEquals(1000 - list.visibleRows(), list.firstVisible());
        list.scroll(Double.NEGATIVE_INFINITY, bounds.x() + 2, bounds.y() + 2);
        list.scroll(Double.MAX_VALUE, bounds.x() + 2, bounds.y() + 2);
        assertEquals(0, list.firstVisible());
    }

    @Test
    void doubleClickRequiresSameRowAndTimeAndCannotRepeatOpen() {
        var list = new WorldSelectionScreen(List.of(
                WorldUiFixtures.entry(1, 0, WorldEntry.Status.READY),
                WorldUiFixtures.entry(2, 0, WorldEntry.Status.READY)));
        list.layout(MINIMUM);
        list.updatePointer(false, false, 0, 0);
        assertEquals(Screen.Action.NONE, click(list, 0, 1));
        assertEquals(Screen.Action.NONE, click(list, 1, 1.1));
        assertEquals(Screen.Action.NONE, click(list, 1, 2));
        assertEquals(new Screen.Action.OpenWorld(new UUID(0, 2)), click(list, 1, 2.2));
        assertEquals(Screen.Action.NONE, list.keyPress(UiKey.ACTIVATE));
    }

    @Test
    void cancellationAndScrollingInvalidateOldClickGestures() {
        var list = new WorldSelectionScreen(WorldUiFixtures.entries(100));
        list.layout(MINIMUM);
        list.updatePointer(false, false, 0, 0);
        var bounds = list.listBounds();
        list.updatePointer(true, true, bounds.x() + 2, bounds.y() + 2);
        list.cancelInteraction();
        assertEquals(Screen.Action.NONE, list.updatePointer(false, false, bounds.x() + 2, bounds.y() + 2));
        click(list, 0, 1);
        list.scroll(-1, bounds.x() + 2, bounds.y() + 2);
        assertEquals(Screen.Action.NONE, click(list, 0, 1.1));
    }

    @Test
    void composesOnlyVisibleRowsAndKeepsButtonsInsideEveryHiDpiLayout() {
        for (int scale = 1; scale <= 4; scale++) {
            var viewport = new UiViewport(640, 480, 1280, 960, scale);
            var list = new WorldSelectionScreen(WorldUiFixtures.entries(1000));
            list.layout(viewport);
            var canvas = new UiCanvas();
            list.render(canvas, viewport);
            assertTrue(canvas.vertexCount() < 10000);
            for (var button : List.of(list.playButton(), list.createButton(), list.backButton())) {
                assertTrue(button.bounds().x() >= 0);
                assertTrue(button.bounds().y() + button.bounds().height() <= viewport.height());
                assertTrue(button.bounds().x() + button.bounds().width() <= viewport.width());
            }
        }
    }

    private static Screen.Action click(WorldSelectionScreen list, int row, double time) {
        var bounds = list.listBounds();
        double x = bounds.x() + 2, y = bounds.y() + row * WorldSelectionScreen.ROW_HEIGHT + 2;
        list.updatePointer(new Screen.Pointer(true, true, false, false, false, x, y, time));
        return list.updatePointer(new Screen.Pointer(false, false, false, false, false, x, y, time + .01));
    }
}
