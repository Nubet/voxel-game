package com.norbertfila.ui;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.chat.ChatLink;
import java.util.ArrayList;
import java.util.List;

public final class ChatOverlay {
    private static final int ROW_HEIGHT = BitmapFont.LINE_HEIGHT + 2;

    record Line(String text, ChatHistory.Kind kind, double time, ChatLink link) {}

    static List<Line> lines(ChatHistory history, int width, boolean open, double now) {
        var result = new ArrayList<Line>();
        for (var message : history.messages()) {
            if (!open && !message.visible(now)) continue;
            String prefix = message.kind() == ChatHistory.Kind.PLAYER ? "<You> " : "";
            String remaining = prefix + message.text();
            int offset = -prefix.length();
            while (!remaining.isEmpty()) {
                int end = BitmapFont.DEFAULT.prefixLength(remaining, width);
                if (end == 0) break;
                ChatLink link = message.link();
                int linkStart = link == null ? 0 : Math.max(0, link.start() - offset);
                int linkEnd = link == null ? 0 : Math.min(end, link.end() - offset);
                ChatLink fragment = linkEnd > linkStart ? new ChatLink(linkStart, linkEnd, link.action()) : null;
                result.add(new Line(remaining.substring(0, end), message.kind(), message.time(), fragment));
                remaining = remaining.substring(end);
                offset += end;
            }
        }
        return result;
    }

    public int render(UiCanvas canvas, UiViewport viewport, ChatHistory history, boolean open, int scroll, double now) {
        var layout = layout(viewport, history, open, scroll, now);
        if (layout == null) return 0;
        int y = layout.y();
        for (int i = layout.start(); i < layout.end(); i++, y += ROW_HEIGHT) {
            var line = layout.lines().get(i);
            float alpha = open ? 1 : (float) Math.clamp((ChatHistory.VISIBLE_SECONDS - (now - line.time())) / 2, 0, 1);
            canvas.rectangle(4, y, layout.width(), ROW_HEIGHT, .03f, .04f, .06f, .75f * alpha);
            boolean error = line.kind() == ChatHistory.Kind.ERROR;
            ChatLink link = line.link();
            if (link == null) {
                canvas.text(BitmapFont.DEFAULT, line.text(), 8, y + 2, 1, error ? .4f : 1, error ? .4f : 1, alpha);
            } else {
                String before = line.text().substring(0, link.start());
                String label = line.text().substring(link.start(), link.end());
                int x = 8 + BitmapFont.DEFAULT.width(before);
                int linkWidth = BitmapFont.DEFAULT.width(label);
                canvas.text(BitmapFont.DEFAULT, before, 8, y + 2, 1, 1, 1, alpha);
                canvas.text(BitmapFont.DEFAULT, label, x, y + 2, .4f, .8f, 1, alpha);
                canvas.rectangle(x, y + BitmapFont.LINE_HEIGHT, linkWidth, 1, .4f, .8f, 1, alpha);
                canvas.text(
                        BitmapFont.DEFAULT, line.text().substring(link.end()), x + linkWidth, y + 2, 1, 1, 1, alpha);
            }
        }
        return layout.scroll();
    }

    public ChatLink.Action actionAt(
            UiViewport viewport, ChatHistory history, int scroll, double now, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
        var layout = layout(viewport, history, true, scroll, now);
        if (layout == null) return null;
        int row = (int) Math.floor((y - layout.y()) / ROW_HEIGHT);
        if (row < 0 || row >= layout.end() - layout.start()) return null;
        var line = layout.lines().get(layout.start() + row);
        ChatLink link = line.link();
        if (link == null) return null;
        int left = 8 + BitmapFont.DEFAULT.width(line.text().substring(0, link.start()));
        int right = left + BitmapFont.DEFAULT.width(line.text().substring(link.start(), link.end()));
        return x >= left && x < right ? link.action() : null;
    }

    private record Layout(List<Line> lines, int width, int scroll, int start, int end, int y) {}

    private static Layout layout(UiViewport viewport, ChatHistory history, boolean open, int scroll, double now) {
        int width = Math.min(420, (int) viewport.width() - 8);
        int bottom = (int) viewport.height() - HudMetrics.HOTBAR_HEIGHT - 10;
        int rows = Math.min(open ? 16 : 8, (bottom - 4) / ROW_HEIGHT);
        if (width < 12 || rows < 1) return null;
        var lines = lines(history, width - 8, open, now);
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - rows));
        int end = lines.size() - scroll;
        int start = Math.max(0, end - rows);
        int y = bottom - (end - start) * ROW_HEIGHT;
        return new Layout(lines, width, scroll, start, end, y);
    }
}
