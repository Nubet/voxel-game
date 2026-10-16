package com.norbertfila.ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.ui.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ChatScreenTest {
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
