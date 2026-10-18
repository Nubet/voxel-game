package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.ui.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ChatScreenTest {
    @Test
    void completesBiomeNamesAndKeepsHistoryAndEscapeSeparate() {
        var history = new ChatHistory();
        history.remember("hello");
        var screen = new ChatScreen(history, "/locate biome flo", text -> fail("Unexpected submission"));
        assertEquals("floating_isles_classic", screen.suggestions().getFirst().text());
        screen.keyPress(UiKey.NEXT);
        assertEquals("/locate biome floating_isles_classic", screen.field().text());
        assertEquals(Screen.Action.NONE, screen.keyPress(UiKey.BACK));
        assertTrue(screen.suggestions().isEmpty());
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
        screen.field().setText("draft");
        screen.keyPress(UiKey.UP);
        assertEquals("hello", screen.field().text());
        screen.keyPress(UiKey.DOWN);
        assertEquals("draft", screen.field().text());
    }

    @Test
    void cyclesCandidatesAndReplacesTheWholeTokenAtTheCaret() {
        var screen = new ChatScreen(new ChatHistory(), "/locate biome ", text -> fail("Unexpected submission"));
        var candidates = screen.suggestions();
        screen.keyPress(UiKey.NEXT);
        assertEquals("/locate biome " + candidates.get(0).text(), screen.field().text());
        screen.keyPress(UiKey.NEXT);
        assertEquals("/locate biome " + candidates.get(1).text(), screen.field().text());
        screen.keyPress(UiKey.PREVIOUS);
        assertEquals("/locate biome " + candidates.get(0).text(), screen.field().text());
        screen.field().setText("/locate biome forest extra");
        for (int i = 0; i < 10; i++) screen.keyPress(UiKey.DECREASE);
        screen.keyPress(UiKey.NEXT);
        assertEquals("/locate biome forest extra", screen.field().text());
        assertEquals(20, screen.field().caret());
    }

    @Test
    void emptyInputClosesWithoutSendingAndTextHasCodePointLimit() {
        var screen = new ChatScreen(new ChatHistory(), "", text -> fail("Unexpected submission"));
        screen.textInput(' ');
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.ACTIVATE));
        screen.field().setText("😀".repeat(ChatHistory.MAX_INPUT));
        screen.textInput('x');
        assertEquals(
                ChatHistory.MAX_INPUT,
                screen.field().text().codePointCount(0, screen.field().text().length()));
        assertThrows(IllegalArgumentException.class, () -> screen.field().setText("x\ny"));
        assertThrows(IllegalArgumentException.class, () -> screen.field().setText("x".repeat(257)));
    }

    @Test
    void submitsOnceAndConsumesClosingKeys() {
        var sent = new ArrayList<String>();
        var screen = new ChatScreen(new ChatHistory(), "/", sent::add);
        var manager = new ScreenManager();
        manager.open(screen);
        "help".codePoints().forEach(screen::textInput);
        manager.keyPress(UiKey.ACTIVATE);
        manager.keyPress(UiKey.ACTIVATE);
        assertEquals(java.util.List.of("/help"), sent);
        assertFalse(manager.isOpen());
        assertTrue(manager.blocksGameplay());
        assertEquals("", ChatScreen.initialText('t'));
        assertEquals("/", ChatScreen.initialText('/'));
        assertNull(ChatScreen.initialText('q'));
    }

    @Test
    void restoresDraftAndEscapeDoesNotSubmit() {
        var history = new ChatHistory();
        history.remember("/help");
        var screen = new ChatScreen(history, "", text -> fail("Unexpected submission"));
        "zażółć😀".codePoints().forEach(screen::textInput);
        screen.keyPress(UiKey.UP);
        assertEquals("/help", screen.field().text());
        screen.keyPress(UiKey.DOWN);
        assertEquals("zażółć😀", screen.field().text());
        assertEquals(Screen.Action.CLOSE, screen.keyPress(UiKey.BACK));
    }
}
