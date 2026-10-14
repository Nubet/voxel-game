package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UiViewportTest {
    @ParameterizedTest
    @CsvSource({"180,100,1", "479,270,1", "960,539,2", "960,540,2", "1280,720,3", "1920,1080,4", "3840,2160,9"})
    void scaleFitsBothAxesAndNeverDropsBelowOne(int width, int height, int scale) {
        UiViewport viewport = new UiViewport(width, height, width, height);
        assertEquals(scale, viewport.scale());
        assertEquals(width, viewport.width() * scale);
        assertEquals(height, viewport.height() * scale);
    }

    @ParameterizedTest
    @CsvSource({"1280,720,1280,720", "640,360,1280,720", "1280,720,1920,1080", "640,480,1280,720", "853,601,1281,903"})
    void cursorMatchesRenderedPixelsWithIndependentDpiOnEachAxis(
            int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
        UiViewport viewport = new UiViewport(windowWidth, windowHeight, framebufferWidth, framebufferHeight);
        double windowX = windowWidth * .317;
        double windowY = windowHeight * .613;
        assertEquals(windowX * framebufferWidth / windowWidth, viewport.cursorX(windowX) * viewport.scale(), 1e-9);
        assertEquals(windowY * framebufferHeight / windowHeight, viewport.cursorY(windowY) * viewport.scale(), 1e-9);
        assertEquals(0, viewport.cursorX(0));
        assertEquals(0, viewport.cursorY(0));
        assertEquals(viewport.width(), viewport.cursorX(windowWidth));
        assertEquals(viewport.height(), viewport.cursorY(windowHeight));
        assertTrue(viewport.cursorX(-1) < 0);
        assertTrue(viewport.cursorY(windowHeight + 1) > viewport.height());
    }

    @Test
    void oddFramebufferDimensionsDoNotStretchIntegerSizedPixels() {
        UiViewport viewport = new UiViewport(1281, 721, 1281, 721);
        assertEquals(3, viewport.scale());
        assertEquals(427, viewport.width());
        assertEquals(721 / 3.0, viewport.height());
        assertEquals(300, 100 / viewport.width() * viewport.framebufferWidth());
    }

    @ParameterizedTest
    @CsvSource({"0,720,1280,720", "1280,0,1280,720", "1280,720,0,720", "1280,720,1280,0", "-1,720,1280,720"})
    void rejectsNonDrawableDimensions(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UiViewport(windowWidth, windowHeight, framebufferWidth, framebufferHeight));
    }

    @Test
    void rejectsNonFiniteCursorCoordinates() {
        UiViewport viewport = new UiViewport(640, 360, 1280, 720);
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> viewport.cursorX(value));
            assertThrows(IllegalArgumentException.class, () -> viewport.cursorY(value));
        }
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,2", "3,3", "4,4"})
    void honorsManualGuiScale(int requested, int expected) {
        UiViewport viewport = new UiViewport(1280, 720, 1920, 1080, requested);

        assertEquals(expected, viewport.scale());
        assertEquals(1920 / (double) expected, viewport.width());
        assertEquals(1080 / (double) expected, viewport.height());
    }

    @Test
    void rejectsUnsupportedManualGuiScale() {
        assertThrows(IllegalArgumentException.class, () -> new UiViewport(640, 360, 640, 360, -1));
        assertThrows(IllegalArgumentException.class, () -> new UiViewport(640, 360, 640, 360, 5));
    }

    @Test
    void clampsManualScaleToTheAvailableLogicalMinimum() {
        UiViewport viewport = new UiViewport(1280, 720, 1280, 720, 4);

        assertEquals(3, viewport.maximumScale());
        assertEquals(3, viewport.scale());
    }
}
