package com.norbertfila.ui;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.player.PlayerState;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.Chunk;
import java.util.List;
import java.util.Locale;

public final class DebugOverlay {
    private static final BitmapFont FONT = BitmapFont.DEFAULT;
    private static final int MARGIN = 10;
    private static final int PADDING = 10;
    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 18;
    private static final int FULL_LAYOUT_HEIGHT = 270;
    private static final int VALUE = 0xE3EAF0;
    private static final int MUTED = 0x93A4B5;
    private static final int ACCENT = 0x87CEC7;
    private static final int GOOD = 0xA8D5A2;
    private static final int WARNING = 0xE8C47D;
    private static final int BAD = 0xEB9292;
    private List<Section> sections = List.of();
    private double nextRefresh;
    private boolean paused;

    record Row(String label, String value, int color) {}

    record Section(String title, List<Row> rows) {}

    public record Data(
            FrameMetrics.OverlaySnapshot performance,
            PlayerState.Position position,
            double yaw,
            double pitch,
            String mode,
            boolean paused,
            double tickMillis,
            int residentSections,
            int visibleSections,
            int drawCalls,
            int uploadQueue,
            int nearDistance,
            int farDistance,
            int simulationDistance) {}

    public void render(UiCanvas canvas, UiViewport viewport, Data data, double nowSeconds) {
        if (sections.isEmpty() || nowSeconds >= nextRefresh || paused != data.paused()) {
            sections = sections(data, viewport);
            nextRefresh = nowSeconds + .25;
            paused = data.paused();
        }
        double scale = pixelScale(viewport) / viewport.scale();
        int firstVertex = canvas.vertexCount();
        int width = (int) Math.floor(viewport.width() / scale);
        int bottom = (int) Math.floor((viewport.height() - HudMetrics.HOTBAR_HEIGHT - MARGIN) / scale);
        int panelWidth = Math.min(PANEL_WIDTH, width - 2 * MARGIN);
        if (panelWidth <= 2 * PADDING || bottom <= MARGIN + 2 * PADDING + ROW_HEIGHT) return;
        boolean columns = width >= 2 * PANEL_WIDTH + 3 * MARGIN;
        int leftY = MARGIN, rightY = MARGIN;
        for (int i = 0; i < sections.size(); i++) {
            boolean right = columns && i >= 2;
            int y = right ? rightY : leftY;
            int height = renderSection(
                    canvas, sections.get(i), right ? width - MARGIN - panelWidth : MARGIN, y, panelWidth, bottom);
            if (right) rightY += height + MARGIN;
            else leftY += height + MARGIN;
        }
        canvas.scaleFrom(firstVertex, scale);
    }

    static double pixelScale(UiViewport viewport) {
        double preferred = viewport.guiScale() == 0
                ? Math.clamp(viewport.framebufferHeight() / 720.0, 1, 2)
                : 1 + (viewport.guiScale() - 1) * .25;
        double fitWidth = viewport.framebufferWidth() / (double) (2 * PANEL_WIDTH + 3 * MARGIN);
        double fitHeight = (viewport.framebufferHeight() - (HudMetrics.HOTBAR_HEIGHT + MARGIN) * viewport.scale())
                / (double) FULL_LAYOUT_HEIGHT;
        return Math.max(1, Math.min(preferred, Math.min(fitWidth, fitHeight)));
    }

    static List<Section> sections(Data data, UiViewport viewport) {
        var performance = data.performance();
        var position = data.position();
        int x = (int) Math.floor(position.x()), y = (int) Math.floor(position.y()), z = (int) Math.floor(position.z());
        String facing = switch (Math.floorMod((int) Math.floor(data.yaw() / 90 + .5), 4)) {
            case 0 -> "east (+X)";
            case 1 -> "south (+Z)";
            case 2 -> "west (-X)";
            default -> "north (-Z)";
        };
        boolean sampling = performance.fps() == 0;
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return List.of(
                new Section(
                        "PERFORMANCE",
                        List.of(
                                new Row(
                                        "FPS",
                                        sampling
                                                ? "Sampling..."
                                                : String.format(Locale.ROOT, "%.0f", performance.fps()),
                                        sampling ? MUTED : rateColor(performance.fps(), 55, 30)),
                                new Row(
                                        "TPS",
                                        data.paused()
                                                ? "Paused"
                                                : sampling
                                                        ? "Sampling..."
                                                        : String.format(
                                                                Locale.ROOT,
                                                                "%.1f / %d",
                                                                performance.tps(),
                                                                FixedSimulation.TICKS_PER_SECOND),
                                        data.paused() || sampling
                                                ? MUTED
                                                : rateColor(
                                                        performance.tps(),
                                                        FixedSimulation.TICKS_PER_SECOND * .95,
                                                        FixedSimulation.TICKS_PER_SECOND * .8)),
                                new Row(
                                        "Frame",
                                        sampling
                                                ? "Sampling..."
                                                : String.format(
                                                        Locale.ROOT,
                                                        "%.2f ms / p95 %.2f",
                                                        performance.frameMillis(),
                                                        performance.p95Millis()),
                                        VALUE),
                                new Row("Tick", String.format(Locale.ROOT, "%.2f ms", data.tickMillis()), VALUE))),
                new Section(
                        "WORLD",
                        List.of(
                                new Row(
                                        "Sections",
                                        data.visibleSections() + " visible / " + data.residentSections(),
                                        VALUE),
                                new Row("Draw calls", Integer.toString(data.drawCalls()), VALUE),
                                new Row("Uploads", data.uploadQueue() + " queued", VALUE),
                                new Row("Near / far", data.nearDistance() + " / " + data.farDistance(), VALUE),
                                new Row("Simulation", data.simulationDistance() + " chunks", VALUE))),
                new Section(
                        "PLAYER",
                        List.of(
                                new Row("Mode", data.mode(), VALUE),
                                new Row(
                                        "XYZ",
                                        String.format(
                                                Locale.ROOT,
                                                "%.2f / %.2f / %.2f",
                                                position.x(),
                                                position.y(),
                                                position.z()),
                                        VALUE),
                                new Row("Block", x + " " + y + " " + z, VALUE),
                                new Row(
                                        "Chunk",
                                        Math.floorDiv(x, Chunk.SIZE) + " " + Math.floorDiv(y, Chunk.SIZE) + " "
                                                + Math.floorDiv(z, Chunk.SIZE),
                                        VALUE),
                                new Row("Facing", facing, VALUE),
                                new Row(
                                        "Yaw / pitch",
                                        String.format(Locale.ROOT, "%.1f / %.1f", data.yaw(), data.pitch()),
                                        VALUE))),
                new Section(
                        "ENGINE",
                        List.of(
                                new Row(
                                        "Java",
                                        Runtime.version().feature() + " / " + System.getProperty("os.arch"),
                                        VALUE),
                                new Row(
                                        "Heap",
                                        used / 1048576 + " / " + runtime.maxMemory() / 1048576 + " MiB",
                                        used > runtime.maxMemory() * .85 ? WARNING : VALUE),
                                new Row(
                                        "Display",
                                        viewport.framebufferWidth() + " x " + viewport.framebufferHeight(),
                                        VALUE))));
    }

    private static int renderSection(UiCanvas canvas, Section section, int x, int y, int width, int bottom) {
        int rows = section.rows().size();
        int height = Math.min(2 * PADDING + ROW_HEIGHT + 6 + rows * ROW_HEIGHT, bottom - y);
        if (height < 2 * PADDING + 2 * ROW_HEIGHT) return 0;
        canvas.rectangle(x, y, width, height, .035f, .05f, .07f, .82f);
        text(canvas, section.title(), x + PADDING, y + PADDING, width - 2 * PADDING, ACCENT);
        canvas.rectangle(x + PADDING, y + PADDING + ROW_HEIGHT, width - 2 * PADDING, 1, .3f, .45f, .5f, .4f);
        int rowY = y + PADDING + ROW_HEIGHT + 6;
        int labelWidth = Math.min(98, (width - 2 * PADDING) / 2);
        for (Row row : section.rows()) {
            if (rowY + BitmapFont.GLYPH_HEIGHT > y + height - PADDING) break;
            text(canvas, row.label(), x + PADDING, rowY, labelWidth - 6, MUTED);
            text(canvas, row.value(), x + PADDING + labelWidth, rowY, width - 2 * PADDING - labelWidth, row.color());
            rowY += ROW_HEIGHT;
        }
        return height;
    }

    private static void text(UiCanvas canvas, String text, int x, int y, int width, int color) {
        canvas.text(
                FONT,
                FONT.ellipsize(text, Math.max(0, width)),
                x,
                y,
                (color >> 16 & 255) / 255f,
                (color >> 8 & 255) / 255f,
                (color & 255) / 255f,
                1);
    }

    private static int rateColor(double value, double warningBelow, double badBelow) {
        return value < badBelow ? BAD : value < warningBelow ? WARNING : GOOD;
    }
}
