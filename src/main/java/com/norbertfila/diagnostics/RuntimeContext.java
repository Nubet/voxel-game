package com.norbertfila.diagnostics;

public final class RuntimeContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RuntimeContext() {}

    public static void set(String value) {
        CURRENT.set(value);
    }

    public static String value() {
        String value = CURRENT.get();
        return value == null ? "unavailable" : value;
    }
}
