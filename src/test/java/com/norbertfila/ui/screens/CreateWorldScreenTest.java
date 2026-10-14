package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.*;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class CreateWorldScreenTest {
    @Test
    void emitsValidatedImmutableRequestOnceWithoutResolvingRandomSeed() {
        var form = new CreateWorldScreen();
        assertFalse(form.createButton().enabled());
        assertEquals(Screen.Action.NONE, form.keyPress(UiKey.ACTIVATE));
        "  Zażółć gęślą jaźń  ".codePoints().forEach(form::textInput);
        assertTrue(form.createButton().enabled());
        assertEquals(
                new Screen.Action.CreateWorld("Zażółć gęślą jaźń", OptionalLong.empty()),
                form.keyPress(UiKey.ACTIVATE));
        form.cancelInteraction();
        assertEquals(Screen.Action.NONE, form.keyPress(UiKey.ACTIVATE));
        form.textInput('X');
        assertFalse(form.nameField().text().endsWith("X"));
    }

    @Test
    void acceptsLongEndpointsAndRejectsOverflowOrPartialNumber() {
        for (String value : new String[] {"-9223372036854775808", "9223372036854775807", "+42", "0", "-0"}) {
            var form = new CreateWorldScreen();
            form.textInput('A');
            form.keyPress(UiKey.NEXT);
            value.codePoints().forEach(form::textInput);
            assertEquals(
                    new Screen.Action.CreateWorld("A", OptionalLong.of(Long.parseLong(value))),
                    form.keyPress(UiKey.ACTIVATE));
        }
        for (String value : new String[] {"9223372036854775808", "-9223372036854775809", "-", "1.2", "abc"}) {
            var form = new CreateWorldScreen();
            form.textInput('A');
            form.keyPress(UiKey.NEXT);
            value.codePoints().forEach(form::textInput);
            assertFalse(form.createButton().enabled(), value);
            assertFalse(form.validationMessage().isEmpty());
            assertEquals(Screen.Action.NONE, form.keyPress(UiKey.ACTIVATE));
        }
    }

    @Test
    void spaceCharactersDoNotActivateFormAndEscapeCancelsWithoutRequest() {
        var form = new CreateWorldScreen();
        var screens = new ScreenManager();
        screens.open(form);
        screens.input(new UiEvent.Character('A'));
        screens.input(new UiEvent.Key(UiKey.SPACE));
        screens.input(new UiEvent.Character(' '));
        screens.input(new UiEvent.Character('B'));
        assertEquals("A B", form.nameField().text());
        assertNull(screens.pollAction());
        screens.input(new UiEvent.Key(UiKey.BACK));
        assertFalse(screens.isOpen());
        assertTrue(screens.blocksGameplay());
        assertNull(screens.pollAction());
        assertThrows(IllegalArgumentException.class, () -> new Screen.Action.CreateWorld("  ", OptionalLong.empty()));
    }

    @Test
    void focusPointerAndViewportChangesCannotCompleteAnOldPress() {
        var form = new CreateWorldScreen();
        var viewport = new UiViewport(640, 360, 1280, 720);
        form.layout(viewport);
        form.textInput('A');
        form.updatePointer(false, false, 0, 0);
        var button = form.createButton().bounds();
        form.updatePointer(true, true, button.x() + 1, button.y() + 1);
        form.layout(new UiViewport(1920, 1080, 1920, 1080, 1));
        button = form.createButton().bounds();
        assertEquals(Screen.Action.NONE, form.updatePointer(false, false, button.x() + 1, button.y() + 1));
        assertEquals("A", form.nameField().text());
        form.updatePointer(true, true, -1, -1);
        form.keyPress(UiKey.PREVIOUS);
        assertEquals(Screen.Action.CLOSE, form.keyPress(UiKey.ACTIVATE));
    }
}
