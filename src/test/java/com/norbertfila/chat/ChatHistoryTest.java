package com.norbertfila.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatHistoryTest {
    @Test
    void boundsMessagesAndExpiresOnlyTheirVisibility() {
        var history = new ChatHistory();
        for (int i = 0; i < 201; i++) history.add("message " + i, ChatHistory.Kind.PLAYER, i);
        assertEquals(200, history.messages().size());
        assertEquals("message 1", history.messages().getFirst().text());
        assertTrue(history.messages().getLast().visible(209));
        assertFalse(history.messages().getLast().visible(210));
        assertEquals(200, history.messages().size());
    }

    @Test
    void boundsInputHistoryAndRestoresDraft() {
        var history = new ChatHistory();
        for (int i = 0; i < 101; i++) history.remember("/command " + i);
        assertEquals("/command 100", history.previous("draft"));
        assertEquals("/command 99", history.previous("ignored"));
        assertEquals("/command 100", history.next());
        assertEquals("draft", history.next());
        for (int i = 0; i < 200; i++) history.previous("draft");
        assertEquals("/command 1", history.previous("draft"));
        history.beginInput();
        assertEquals("", history.next());
    }
}
