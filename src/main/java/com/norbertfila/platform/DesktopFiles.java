package com.norbertfila.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class DesktopFiles {
    private DesktopFiles() {}

    public static CompletableFuture<Void> open(Path path) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        if (!Files.isRegularFile(path)) throw new IOException("File not found: " + path.getFileName());
                        if (!Desktop.isDesktopSupported()
                                || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                            throw new IOException("Opening files is not supported on this desktop");
                        Desktop.getDesktop().open(path.toFile());
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                },
                task -> Thread.ofVirtual().name("desktop-file-opener").start(task));
    }
}
