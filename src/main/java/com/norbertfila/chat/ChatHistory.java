package com.norbertfila.chat;

import java.util.ArrayList;
import java.util.List;

public final class ChatHistory {
    public static final int MAX_INPUT = 256;
    public static final double VISIBLE_SECONDS = 10;
    private final ArrayList<Message> messages = new ArrayList<>();
    private final ArrayList<String> inputs = new ArrayList<>();
    private int cursor;
    private String draft = "";

    public enum Kind {
        PLAYER,
        SYSTEM,
        ERROR
    }

    public record Message(String text, Kind kind, double time) {
        public boolean visible(double now) {
            return now - time < VISIBLE_SECONDS;
        }
    }

    public void add(String text, Kind kind, double now) {
        if (text == null || kind == null || !Double.isFinite(now))
            throw new IllegalArgumentException("Invalid message");
        if (messages.size() == 200) messages.removeFirst();
        messages.add(new Message(text, kind, now));
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    public void remember(String text) {
        if (text.isBlank()) return;
        if (text.codePointCount(0, text.length()) > MAX_INPUT) throw new IllegalArgumentException("Input too long");
        if (inputs.size() == 100) inputs.removeFirst();
        inputs.add(text);
        beginInput();
    }

    public void beginInput() {
        cursor = inputs.size();
        draft = "";
    }

    public String previous(String current) {
        if (cursor == inputs.size()) draft = current;
        if (cursor > 0) cursor--;
        return cursor < inputs.size() ? inputs.get(cursor) : draft;
    }

    public String next() {
        if (cursor < inputs.size()) cursor++;
        return cursor < inputs.size() ? inputs.get(cursor) : draft;
    }
}
