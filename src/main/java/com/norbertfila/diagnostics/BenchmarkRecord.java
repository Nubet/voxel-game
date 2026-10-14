package com.norbertfila.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Ordered named benchmark values written as one CSV row. Built once per run, outside measured frames. */
public final class BenchmarkRecord {
    private final Map<String, String> values = new LinkedHashMap<>();

    public BenchmarkRecord put(String name, Object value) {
        Objects.requireNonNull(name);
        String text =
                value instanceof Double number ? String.format(Locale.ROOT, "%.3f", number) : String.valueOf(value);
        if (values.putIfAbsent(name, text.replace(',', ';')) != null)
            throw new IllegalArgumentException("Duplicate benchmark value: " + name);
        return this;
    }

    public BenchmarkRecord put(String prefix, FrameProfiler.Distribution distribution) {
        put(prefix + "P50Ms", distribution.p50Millis());
        put(prefix + "P95Ms", distribution.p95Millis());
        put(prefix + "P99Ms", distribution.p99Millis());
        return put(prefix + "MaxMs", distribution.maxMillis());
    }

    public String get(String name) {
        return values.get(name);
    }

    public String header() {
        return String.join(",", values.keySet());
    }

    public String row() {
        return String.join(",", values.values());
    }

    public String summary(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> name + "=" + values.get(name))
                .collect(Collectors.joining(" "));
    }

    /** Appends the row, writing the header first when the file is new or its header differs. */
    public void appendTo(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        String header = header();
        boolean writeHeader = !Files.exists(file)
                || !Files.readAllLines(file, StandardCharsets.UTF_8).contains(header);
        String text = (writeHeader ? header + System.lineSeparator() : "") + row() + System.lineSeparator();
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
