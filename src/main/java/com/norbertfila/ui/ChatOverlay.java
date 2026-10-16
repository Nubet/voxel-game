package com.norbertfila.ui;

import com.norbertfila.chat.ChatHistory;
import java.util.ArrayList;
import java.util.List;

public final class ChatOverlay {
    private static final int ROW_HEIGHT = BitmapFont.LINE_HEIGHT + 2;

    record Line(String text, ChatHistory.Kind kind, double time) {}

    static List<Line> lines(ChatHistory history, int width, boolean open, double now) {
        var result = new ArrayList<Line>();
        for (var message : history.messages()) {
            if (!open && !message.visible(now)) continue;
            String remaining = message.kind() == ChatHistory.Kind.PLAYER ? "<You> " + message.text() : message.text();
            while (!remaining.isEmpty()) {
                int end = BitmapFont.DEFAULT.prefixLength(remaining, width);
                if (end == 0) break;
                result.add(new Line(remaining.substring(0, end), message.kind(), message.time()));
                remaining = remaining.substring(end);
            }
        }
        return result;
    }

    public int render(UiCanvas canvas, UiViewport viewport, ChatHistory history, boolean open, int scroll, double now) {
        int width = Math.min(420, (int) viewport.width() - 8);
        int bottom = (int) viewport.height() - HudMetrics.HOTBAR_HEIGHT - 10;
        int rows = Math.min(open ? 16 : 8, (bottom - 4) / ROW_HEIGHT);
        if (width < 12 || rows < 1) return 0;
        var lines = lines(history, width - 8, open, now);
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - rows));
        int end = lines.size() - scroll;
        int start = Math.max(0, end - rows);
        int y = bottom - (end - start) * ROW_HEIGHT;
        for (int i = start; i < end; i++, y += ROW_HEIGHT) {
            var line = lines.get(i);
            float alpha = open ? 1 : (float) Math.clamp((ChatHistory.VISIBLE_SECONDS - (now - line.time())) / 2, 0, 1);
            canvas.rectangle(4, y, width, ROW_HEIGHT, .03f, .04f, .06f, .75f * alpha);
            boolean error = line.kind() == ChatHistory.Kind.ERROR;
            canvas.text(BitmapFont.DEFAULT, line.text(), 8, y + 2, 1, error ? .4f : 1, error ? .4f : 1, alpha);
        }
        return scroll;
    }
}
