package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.chat.ChatHistory;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class ChatOverlayTest {
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
