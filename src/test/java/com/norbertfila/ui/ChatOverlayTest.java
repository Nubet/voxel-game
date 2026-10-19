package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.chat.ChatLink;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChatOverlayTest {
    @Test
    void clickingBiomeCoordinatesReturnsTheSuggestedTeleport() {
        var history = new ChatHistory();
        String prefix = "Nearest forest at ";
        String coordinates = "X=-10 Z=20";
        var suggestion = new ChatLink.SuggestCommand("/tp -9.5 71 20.5");
        history.add(
                prefix + coordinates + " Surface Y=70.",
                ChatHistory.Kind.SYSTEM,
                0,
                new ChatLink(prefix.length(), prefix.length() + coordinates.length(), suggestion));
        var viewport = new UiViewport(640, 360, 1280, 720);
        int y = (int) viewport.height() - HudMetrics.HOTBAR_HEIGHT - 10 - BitmapFont.LINE_HEIGHT;
        int x = 8 + BitmapFont.DEFAULT.width(prefix);
        var overlay = new ChatOverlay();
        assertEquals(suggestion, overlay.actionAt(viewport, history, 0, 0, x, y));
        assertNull(overlay.actionAt(viewport, history, 0, 0, x - 1, y));
        assertNull(overlay.actionAt(viewport, history, 0, 0, x + BitmapFont.DEFAULT.width(coordinates), y));
    }

    @Test
    void linksKeepTheirActionAndExactHitBoundsAcrossWrappedRowsAndScroll() {
        var history = new ChatHistory();
        var action = new ChatLink.OpenFile(Path.of("screenshots", "2026-10-19_12.30.45.png"));
        String prefix = "Saved screenshot as ";
        String message = prefix + action.path().getFileName();
        history.add(message, ChatHistory.Kind.SYSTEM, 0, new ChatLink(prefix.length(), message.length(), action));
        for (int i = 0; i < 20; i++) history.add("new message " + i, ChatHistory.Kind.SYSTEM, 0);
        var overlay = new ChatOverlay();
        for (int width : new int[] {110, 640}) {
            var viewport = new UiViewport(width, 720, width * 2, 1440);
            var lines = ChatOverlay.lines(history, Math.min(420, (int) viewport.width() - 8) - 8, true, 20);
            int rows = 16;
            int scroll = lines.size() - rows;
            int top = (int) viewport.height() - HudMetrics.HOTBAR_HEIGHT - 10 - rows * (BitmapFont.LINE_HEIGHT + 2);
            int linkedRows = 0;
            for (int row = 0; row < Math.min(rows, lines.size()); row++) {
                var line = lines.get(row);
                int y = top + row * (BitmapFont.LINE_HEIGHT + 2) + 2;
                if (line.link() == null) {
                    assertNull(overlay.actionAt(viewport, history, scroll, 20, 8, y));
                    continue;
                }
                linkedRows++;
                int left = 8
                        + BitmapFont.DEFAULT.width(
                                line.text().substring(0, line.link().start()));
                int right = left
                        + BitmapFont.DEFAULT.width(line.text()
                                .substring(line.link().start(), line.link().end()));
                assertEquals(action, overlay.actionAt(viewport, history, scroll, 20, left, y));
                assertNull(overlay.actionAt(viewport, history, scroll, 20, left - 1, y));
                assertNull(overlay.actionAt(viewport, history, scroll, 20, right, y));
            }
            assertTrue(linkedRows > 0);
            assertNull(overlay.actionAt(viewport, history, 0, 20, 8, top));
            assertNull(overlay.actionAt(viewport, history, scroll, 20, 8, top - 1));
            assertNull(overlay.actionAt(viewport, history, scroll, 20, Double.NaN, top));
        }
    }

    @Test
    void rejectsInvalidRangesAndDoesNotInferLinksFromPlayerText() {
        var history = new ChatHistory();
        var action = new ChatLink.OpenFile(Path.of("image.png"));
        assertThrows(IllegalArgumentException.class, () -> new ChatLink(-1, 1, action));
        assertThrows(IllegalArgumentException.class, () -> new ChatLink(1, 1, action));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.add("short", ChatHistory.Kind.SYSTEM, 0, new ChatLink(0, 6, action)));
        history.add("Saved screenshot as image.png", ChatHistory.Kind.PLAYER, 0);
        assertTrue(ChatOverlay.lines(history, 1000, true, 0).stream().allMatch(line -> line.link() == null));
    }

    @Test
    void wrapsAndRestoresExpiredMessagesWhenOpened() {
        var history = new ChatHistory();
        history.add("a long message that wraps across lines", ChatHistory.Kind.PLAYER, 0);
        assertTrue(ChatOverlay.lines(history, 60, true, 20).size() > 1);
        assertTrue(ChatOverlay.lines(history, 60, false, 20).isEmpty());
        for (var line : ChatOverlay.lines(history, 60, true, 20))
            assertTrue(BitmapFont.DEFAULT.width(line.text()) <= 60);
    }

    @Test
    void clampsScrollAndKeepsGeometryAboveHotbar() {
        var history = new ChatHistory();
        for (int i = 0; i < 40; i++) history.add("message " + i, ChatHistory.Kind.SYSTEM, 0);
        var overlay = new ChatOverlay();
        var viewport = new UiViewport(640, 360, 640, 360);
        var canvas = new UiCanvas();
        assertEquals(24, overlay.render(canvas, viewport, history, true, 1000, 0));
        var buffer = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(buffer);
        for (int i = 0; i < buffer.position(); i += UiCanvas.FLOATS_PER_VERTEX) {
            assertTrue(buffer.get(i) >= 0 && buffer.get(i) <= viewport.width());
            assertTrue(buffer.get(i + 1) >= 0 && buffer.get(i + 1) < viewport.height() - HudMetrics.HOTBAR_HEIGHT);
        }
    }
}
