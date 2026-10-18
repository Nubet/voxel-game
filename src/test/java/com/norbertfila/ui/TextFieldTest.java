package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class TextFieldTest {
    @Test
    void replacementPreservesSuffixAndRejectsInvalidRangesWithoutChangingText() {
        var field = new TextField(new UiBounds(0, 0, 80, 20), 10);
        field.setText("A🌲tail");
        assertThrows(IllegalArgumentException.class, () -> field.replaceRange(2, 3, "x"));
        assertThrows(IllegalArgumentException.class, () -> field.replaceRange(1, 2, "x"));
        assertThrows(NullPointerException.class, () -> field.replaceRange(1, 3, null));
        assertThrows(IllegalArgumentException.class, () -> field.replaceRange(1, 3, "x".repeat(10)));
        assertEquals("A🌲tail", field.text());
        field.replaceRange(1, 3, "ok");
        assertEquals("Aoktail", field.text());
        assertEquals(3, field.caret());
    }

    @Test
    void editsUnicodeScalarsWithoutSplittingSurrogates() {
        var field = new TextField(new UiBounds(10, 20, 100, 20), 64);
        field.textInput('x');
        assertEquals("", field.text());
        field.setFocused(true);
        "ą🌲Z".codePoints().forEach(field::textInput);
        field.keyPress(UiKey.DECREASE);
        field.keyPress(UiKey.BACKSPACE);
        assertEquals("ąZ", field.text());
        field.textInput('ł');
        assertEquals("ąłZ", field.text());
        field.keyPress(UiKey.HOME);
        field.keyPress(UiKey.DELETE);
        assertEquals("łZ", field.text());
        field.keyPress(UiKey.END);
        field.keyPress(UiKey.DELETE);
        field.keyPress(UiKey.INCREASE);
        field.keyPress(UiKey.BACKSPACE);
        assertEquals("ł", field.text());
    }

    @Test
    void boundsCodePointsAndRejectsNonScalarOrMultilineInput() {
        var field = new TextField(new UiBounds(0, 0, 80, 20), 64);
        field.setFocused(true);
        for (int cp : new int[] {-1, 0x110000, 0xD800, 0, '\t', '\n', 0x2028, 0x2029}) field.textInput(cp);
        assertEquals("", field.text());
        "🌲".repeat(65).codePoints().forEach(field::textInput);
        assertEquals(64, field.text().codePointCount(0, field.text().length()));
        assertEquals(128, field.caret());
        field.keyPress(UiKey.BACKSPACE);
        field.textInput('ę');
        assertTrue(field.text().endsWith("ę"));
    }

    @Test
    void scrollsCaretWithinBoundsAndPointerPlacesItAtAGlyphBoundary() {
        var field = new TextField(new UiBounds(10, 20, 80, 20), 64);
        field.setFocused(true);
        "Zażółć🌲".repeat(8).codePoints().forEach(field::textInput);
        var canvas = new UiCanvas();
        field.render(canvas);
        var vertices = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(vertices);
        for (int index = 0; index < canvas.floatCount(); index += UiCanvas.FLOATS_PER_VERTEX) {
            assertTrue(vertices.get(index) >= 10 && vertices.get(index) <= 90);
            assertTrue(vertices.get(index + 1) >= 20 && vertices.get(index + 1) <= 40);
        }
        field.keyPress(UiKey.HOME);
        field.render(new UiCanvas());
        field.updatePointer(true, 14 + BitmapFont.DEFAULT.advance('Z') + .1, 25);
        assertEquals(1, field.caret());
        field.updatePointer(true, 200, 25);
        assertFalse(field.focused());
    }

    @Test
    void editsBeforeTheScrolledWindowKeepItsStartOnAScalarBoundary() {
        var field = new TextField(new UiBounds(0, 0, 80, 20), 64);
        field.setFocused(true);
        "🌲".repeat(40).codePoints().forEach(field::textInput);
        field.render(new UiCanvas());
        field.keyPress(UiKey.HOME);
        field.textInput('A');
        field.keyPress(UiKey.END);
        field.keyPress(UiKey.DECREASE);
        var canvas = new UiCanvas();
        field.render(canvas);
        var vertices = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(vertices);
        int advance = BitmapFont.DEFAULT.advance(0x1F332);
        assertEquals(
                4 + (70 / advance - 1) * advance, vertices.get(canvas.floatCount() - 6 * UiCanvas.FLOATS_PER_VERTEX));
        for (int index = 0; index < canvas.floatCount(); index += UiCanvas.FLOATS_PER_VERTEX)
            assertTrue(vertices.get(index) >= 0 && vertices.get(index) <= 80);
        field.keyPress(UiKey.HOME);
        field.keyPress(UiKey.DELETE);
        assertEquals("🌲".repeat(40), field.text());
    }
}
