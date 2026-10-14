package com.norbertfila.persistence.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileTest {
    @TempDir
    Path directory;

    @Test
    void serializationFailurePreservesCommittedBytesAndCleansPartialFile() throws Exception {
        Path target = directory.resolve("state.vxl");
        byte[] committed = {1, 2, 3};
        Files.write(target, committed);
        var failure = assertThrows(
                IOException.class,
                () -> AtomicFile.replace(target, channel -> {
                    channel.write(ByteBuffer.wrap(new byte[] {4, 5}));
                    throw new IOException("disk full");
                }));
        assertEquals("disk full", failure.getMessage());
        assertArrayEquals(committed, Files.readAllBytes(target));
        assertOnly(target);
    }

    @Test
    void forceFailureDoesNotReachCommit() throws Exception {
        Path target = directory.resolve("state.vxl");
        Files.write(target, new byte[] {1});
        assertThrows(
                IOException.class,
                () -> AtomicFile.replace(
                        target,
                        channel -> {
                            channel.write(ByteBuffer.wrap(new byte[] {2}));
                            channel.close();
                        },
                        (temporary, destination) -> fail("Must force the file before publishing it")));
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(target));
        assertOnly(target);
    }

    @Test
    void unsupportedAtomicMoveNeverFallsBackToUnsafeReplacement() throws Exception {
        Path target = directory.resolve("state.vxl");
        Files.write(target, new byte[] {1});
        assertThrows(
                AtomicMoveNotSupportedException.class,
                () -> AtomicFile.replace(
                        target, channel -> channel.write(ByteBuffer.wrap(new byte[] {2})), (temporary, destination) -> {
                            assertEquals(destination.getParent(), temporary.getParent());
                            assertArrayEquals(new byte[] {2}, Files.readAllBytes(temporary));
                            throw new AtomicMoveNotSupportedException(
                                    temporary.toString(), destination.toString(), "injected");
                        }));
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(target));
        assertOnly(target);
    }

    @Test
    void failureBeforeFirstPublicationDoesNotCreateCommittedSave() throws Exception {
        Path target = directory.resolve("state.vxl");
        assertThrows(
                IOException.class,
                () -> AtomicFile.replace(
                        target, channel -> channel.write(ByteBuffer.wrap(new byte[] {2})), (temporary, destination) -> {
                            throw new IOException("publication failed");
                        }));
        assertFalse(Files.exists(target));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void readerSeesOldSnapshotUntilCommitAndCompleteNewSnapshotAfterwards() throws Exception {
        Path target = directory.resolve("state.vxl");
        var identity = new WorldIdentity(UUID.randomUUID(), 42, "default", 11);
        var old = WorldSnapshot.initial(identity, Instant.ofEpochMilli(1));
        var next = WorldSnapshot.initial(identity, Instant.ofEpochMilli(2));
        WorldStateCodec.write(target, old);
        Path source = directory.resolve("next.vxl");
        WorldStateCodec.write(source, next);
        var ready = new CountDownLatch(1);
        var publish = new CountDownLatch(1);
        var staged = new AtomicReference<Path>();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var work = worker.submit(() -> {
                AtomicFile.replace(
                        target,
                        channel -> {
                            byte[] bytes = Files.readAllBytes(source);
                            var buffer = ByteBuffer.wrap(bytes);
                            while (buffer.hasRemaining()) channel.write(buffer);
                        },
                        (temporary, destination) -> {
                            staged.set(temporary);
                            ready.countDown();
                            try {
                                if (!publish.await(5, TimeUnit.SECONDS))
                                    throw new IOException("Test publication timeout");
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IOException(interrupted);
                            }
                            Files.move(
                                    temporary,
                                    destination,
                                    StandardCopyOption.ATOMIC_MOVE,
                                    StandardCopyOption.REPLACE_EXISTING);
                        });
                return null;
            });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                assertEquals(next, WorldStateCodec.read(staged.get(), identity));
                assertEquals(old, WorldStateCodec.read(target, identity));
            } finally {
                publish.countDown();
            }
            work.get(5, TimeUnit.SECONDS);
        }
        assertEquals(next, WorldStateCodec.read(target, identity));
        assertFalse(Files.exists(staged.get()));
    }

    @Test
    void abandonedTemporaryFileIsNeverSelectedAsCommittedState() throws Exception {
        Path target = directory.resolve("state.vxl");
        var identity = new WorldIdentity(UUID.randomUUID(), 42, "default", 11);
        var old = WorldSnapshot.initial(identity, Instant.ofEpochMilli(1));
        var next = WorldSnapshot.initial(identity, Instant.ofEpochMilli(2));
        WorldStateCodec.write(target, old);
        Path abandoned = directory.resolve(".state-abandoned.tmp");
        WorldStateCodec.write(abandoned, next);
        assertEquals(old, WorldStateCodec.read(target, identity));
        Files.delete(target);
        assertThrows(IOException.class, () -> WorldStateCodec.read(target, identity));
        assertEquals(next, WorldStateCodec.read(abandoned, identity));
    }

    @Test
    void processDeathBeforeAndAfterCommitLeavesAnUnambiguousSnapshot() throws Exception {
        Path target = directory.resolve("state.vxl");
        Path nextFile = directory.resolve("next.vxl");
        var identity = new WorldIdentity(UUID.randomUUID(), 42, "default", 11);
        var old = WorldSnapshot.initial(identity, Instant.ofEpochMilli(1));
        var next = WorldSnapshot.initial(identity, Instant.ofEpochMilli(2));
        WorldStateCodec.write(target, old);
        WorldStateCodec.write(nextFile, next);
        String classes = Path.of(AtomicFile.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                + java.io.File.pathSeparator
                + Path.of(AtomicFileTest.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
        for (String point : java.util.List.of("before", "after")) {
            Process process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java")
                                    .toString(),
                            "-cp",
                            classes,
                            CrashWriter.class.getName(),
                            target.toString(),
                            nextFile.toString(),
                            point)
                    .start();
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Crash writer did not exit");
                assertEquals(
                        73,
                        process.exitValue(),
                        new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            assertEquals(point.equals("before") ? old : next, WorldStateCodec.read(target, identity));
        }
    }

    public static final class CrashWriter {
        public static void main(String[] args) throws Exception {
            AtomicFile.replace(
                    Path.of(args[0]),
                    channel -> {
                        var bytes = ByteBuffer.wrap(Files.readAllBytes(Path.of(args[1])));
                        while (bytes.hasRemaining()) channel.write(bytes);
                    },
                    (temporary, destination) -> {
                        if (args[2].equals("before")) Runtime.getRuntime().halt(73);
                        Files.move(
                                temporary,
                                destination,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                        Runtime.getRuntime().halt(73);
                    });
        }
    }

    private void assertOnly(Path target) throws IOException {
        try (var files = Files.list(directory)) {
            assertEquals(java.util.List.of(target), files.toList());
        }
    }
}
