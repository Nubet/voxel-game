package com.norbertfila.persistence.world;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class AtomicFile {
    private AtomicFile() {}

    static void replace(Path target, Writer writer) throws IOException {
        replace(
                target,
                writer,
                (temporary, destination) -> Files.move(
                        temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    static void replace(Path target, Writer writer, Commit commit) throws IOException {
        Path destination = target.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".state-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                writer.write(channel);
                channel.force(true);
            }
            // A non-atomic fallback could destroy the only committed snapshot.
            commit.move(temporary, destination);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @FunctionalInterface
    interface Writer {
        void write(FileChannel channel) throws IOException;
    }

    @FunctionalInterface
    interface Commit {
        void move(Path temporary, Path destination) throws IOException;
    }
}
