package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerState;
import java.nio.FloatBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DebugOverlayTest {
    @Test
    void displaysNegativeBlockAndChunkCoordinatesAndActualSimulationTarget() {
        var lines = lines(data(false, -90));
        assertTrue(lines.contains("Block: -1 -17 16"));
        assertTrue(lines.contains("Biome: TAIGA"));
        assertTrue(lines.contains("Chunk: -1 -2 1"));
        assertTrue(lines.contains("XYZ: -0.25 / -16.50 / 16.00"));
        assertTrue(lines.stream().anyMatch(line -> line.contains("TPS: 30.0 / 60")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("north (-Z)")));
        assertTrue(lines(data(true, 0)).contains("TPS: Paused"));
    }

    @ParameterizedTest
    @CsvSource({"0,east (+X)", "90,south (+Z)", "180,west (-X)", "-90,north (-Z)", "-450,north (-Z)"})
    void facingUsesCameraYawConvention(double yaw, String direction) {
        assertTrue(lines(data(false, yaw)).stream().anyMatch(line -> line.contains(direction)));
    }

    @ParameterizedTest
    @CsvSource({"1920,1080,0", "1280,720,1", "1280,720,3", "640,480,2", "320,240,0", "180,100,0", "3,20,0"})
    void textAndBackgroundStayInsideScaledViewportAndAboveHotbar(int width, int height, int scale) {
        var viewport = new UiViewport(width, height, width, height, scale);
        var canvas = new UiCanvas();
        new DebugOverlay().render(canvas, viewport, data(false, -90), 0);
        var vertices = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(vertices);
        float[] values = vertices.array();
        for (int i = 0; i < values.length; i += UiCanvas.FLOATS_PER_VERTEX) {
            assertTrue(values[i] >= 0 && values[i] <= viewport.width());
            assertTrue(values[i + 1] >= 0 && values[i + 1] <= viewport.height() - HudMetrics.HOTBAR_HEIGHT);
        }
        if (width >= 180) assertTrue(canvas.vertexCount() > 0);
    }

    private static DebugOverlay.Data data(boolean paused, double yaw) {
        return new DebugOverlay.Data(
                new FrameMetrics.OverlaySnapshot(120, 8.33, 12, 30),
                new PlayerState.Position(-.25, -16.5, 16),
                yaw,
                -25,
                "CREATIVE",
                paused,
                2.5,
                100,
                40,
                12,
                3,
                8,
                32,
                4,
                BiomeType.TAIGA,
                com.norbertfila.mob.MobCensus.EMPTY,
                com.norbertfila.light.LightSample.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({"720,1,1", "720,2,1.25", "720,3,1.5", "1080,4,1.75", "720,0,1", "1080,0,1.5", "2160,0,2"})
    void descriptiveSizesGrowGentlyAndAutoDoesNotUseMenuMultiplier(int height, int guiScale, double expectedScale) {
        var viewport = new UiViewport(1920, height, 1920, height, guiScale);
        assertEquals(expectedScale, DebugOverlay.pixelScale(viewport));
        var canvas = new UiCanvas();
        new DebugOverlay().render(canvas, viewport, data(false, -90), 0);
        var vertices = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(vertices);
        assertEquals(10 * expectedScale, vertices.get(0) * viewport.scale(), .001);
    }

    @Test
    void groupsValuesAndDistinguishesSlowSimulationFromHealthyFps() {
        var sections = DebugOverlay.sections(data(false, -90), new UiViewport(1280, 720, 1280, 720));
        assertEquals(
                List.of("PERFORMANCE", "WORLD", "PLAYER", "ENGINE"),
                sections.stream().map(DebugOverlay.Section::title).toList());
        var performance = sections.getFirst().rows();
        assertNotEquals(performance.get(0).color(), performance.get(1).color());
        assertNotEquals(performance.get(0).color(), performance.get(2).color());
    }

    @Test
    void oneTimesScaleProducesFourPaddedPanelsAndLeavesScreenCenterClear() {
        var viewport = new UiViewport(1280, 720, 1280, 720, 1);
        var canvas = new UiCanvas();
        new DebugOverlay().render(canvas, viewport, data(false, -90), 0);
        var buffer = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(buffer);
        float[] vertices = buffer.array();
        int panels = 0;
        for (int i = 0; i < vertices.length; i += 6 * UiCanvas.FLOATS_PER_VERTEX) {
            if (Math.abs(vertices[i + 5] - .82f) > .001f) continue;
            panels++;
            float left = vertices[i], right = vertices[i + UiCanvas.FLOATS_PER_VERTEX];
            assertTrue(left >= 10 && right <= viewport.width() - 10);
            assertTrue(right < viewport.width() / 2 || left > viewport.width() / 2);
            assertEquals(300, right - left);
        }
        assertEquals(4, panels);
    }

    @ParameterizedTest
    @CsvSource({
        "640,360,0",
        "1280,720,0",
        "1920,1080,0",
        "2560,1440,0",
        "3840,2160,0",
        "1280,720,1",
        "1280,720,2",
        "1280,720,3",
        "1280,720,4"
    })
    void allFourSectionsAndEveryRowFitAtAutoAndManualSizes(int width, int height, int size) {
        var viewport = new UiViewport(width, height, width, height, size);
        var sample = data(false, -90);
        var canvas = new UiCanvas();
        new DebugOverlay().render(canvas, viewport, sample, 0);
        var buffer = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(buffer);
        float[] vertices = buffer.array();
        int panels = 0, labelGlyphs = 0;
        for (int i = 0; i < vertices.length; i += 6 * UiCanvas.FLOATS_PER_VERTEX) {
            if (Math.abs(vertices[i + 5] - .82f) < .001f) panels++;
            if (Math.abs(vertices[i + 2] - 0x93 / 255f) < .001f
                    && Math.abs(vertices[i + 3] - 0xA4 / 255f) < .001f
                    && Math.abs(vertices[i + 4] - 0xB5 / 255f) < .001f) labelGlyphs++;
            for (int v = i; v < i + 6 * UiCanvas.FLOATS_PER_VERTEX; v += UiCanvas.FLOATS_PER_VERTEX) {
                assertTrue(vertices[v] >= 0 && vertices[v] <= viewport.width());
                assertTrue(vertices[v + 1] >= 0 && vertices[v + 1] <= viewport.height() - HudMetrics.HOTBAR_HEIGHT);
            }
        }
        long expectedLabels = DebugOverlay.sections(sample, viewport).stream()
                .flatMap(section -> section.rows().stream())
                .flatMapToInt(row -> row.label().codePoints())
                .filter(cp -> cp != ' ')
                .count();
        assertEquals(4, panels);
        assertEquals(expectedLabels, labelGlyphs);
    }

    private static List<String> lines(DebugOverlay.Data data) {
        return DebugOverlay.sections(data, new UiViewport(1280, 720, 1280, 720)).stream()
                .flatMap(section -> section.rows().stream())
                .map(row -> row.label() + ": " + row.value())
                .toList();
    }
}
