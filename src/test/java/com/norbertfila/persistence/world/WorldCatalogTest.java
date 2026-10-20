package com.norbertfila.persistence.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldCatalogTest {
    @TempDir
    Path directory;

    @Test
    void missingRootListsEmptyWithoutCreatingAnything() throws Exception {
        Path root = directory.resolve("worlds");
        assertEquals(List.of(), new WorldCatalog(root, "default", 11).list());
        assertFalse(Files.exists(root));
        Files.write(root, new byte[] {1});
        assertThrows(IOException.class, () -> new WorldCatalog(root, "default", 11).list());
    }

    @Test
    void duplicateNamesAndPathLikeNamesHaveIndependentUuidDirectoriesAndState() throws Exception {
        var catalog = catalog();
        var first = metadata(1, "../Łąka", 42);
        var second = metadata(2, "../Łąka", -42);
        var a = catalog.create(first);
        var b = catalog.create(second);
        assertNotEquals(a.id(), b.id());
        assertEquals(a.name(), b.name());
        assertEquals(WorldEntry.Status.READY, a.status());
        assertEquals(first, WorldMetadataCodec.read(folder(first).resolve("world.meta")));
        assertEquals(
                WorldSnapshot.initial(second.identity(), second.createdAt()),
                WorldStateCodec.read(state(second), second.identity()));
        var changed = new WorldSnapshot(
                first.identity(),
                1,
                Instant.ofEpochMilli(3),
                List.of(new ChunkDelta(
                        new ChunkPos(0, 0, 0),
                        List.of(new ChunkDelta.BlockChange(new BlockPos(1, 1, 1), BlockId.STONE)))),
                FluidSnapshot.EMPTY,
                InventorySnapshot.EMPTY,
                Optional.of(new SavedPlayerState(1, 40, 1, 0, 0, Mode.SURVIVAL, false)));
        WorldStateCodec.write(state(first), changed);
        assertEquals(changed, WorldStateCodec.read(state(first), first.identity()));
        assertEquals(
                List.of(),
                WorldStateCodec.read(state(second), second.identity()).deltas());
        assertEquals(2, catalog.list().size());
        try (var files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void sortingUsesCommittedTimestampThenUuidNotFileMtime() throws Exception {
        var catalog = catalog();
        var first = metadata(1, "A", 1);
        var second = metadata(2, "B", 2);
        var third = metadata(3, "C", 3);
        catalog.create(third);
        catalog.create(second);
        catalog.create(first);
        WorldStateCodec.write(state(second), WorldSnapshot.initial(second.identity(), Instant.ofEpochMilli(10)));
        Files.setLastModifiedTime(state(first), FileTime.fromMillis(Long.MAX_VALUE / 1000));
        var entries = catalog.list();
        assertEquals(
                List.of(
                        second.identity().id(),
                        first.identity().id(),
                        third.identity().id()),
                entries.stream().map(WorldEntry::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> entries.clear());
    }

    @Test
    void ignoresUnpublishedForeignAndLegacyEntries() throws Exception {
        var catalog = catalog();
        var valid = metadata(1, "A", 1);
        catalog.create(valid);
        Files.createDirectory(directory.resolve(".creating-" + UUID.randomUUID()));
        Files.createDirectory(directory.resolve("foreign"));
        Files.createDirectory(directory.resolve("1-1-1-1-1"));
        Files.write(directory.resolve("old.vxl"), new byte[] {1});
        Files.write(directory.resolve(UUID.randomUUID().toString()), new byte[] {1});
        assertEquals(
                List.of(valid.identity().id()),
                catalog.list().stream().map(WorldEntry::id).toList());
    }

    @Test
    void brokenAndUnsupportedEntriesDoNotPreventHealthyWorldsFromListing() throws Exception {
        var catalog = catalog();
        var good = metadata(1, "Good", 1);
        var missing = metadata(2, "Missing", 2);
        var corrupt = metadata(3, "Corrupt", 3);
        var future = metadata(4, "Future", 4);
        var truncated = metadata(5, "Truncated state", 5);
        for (var metadata : List.of(good, missing, corrupt, future, truncated)) catalog.create(metadata);
        Files.delete(state(missing));
        Files.write(folder(corrupt).resolve("world.meta"), new byte[] {0});
        byte[] bytes = Files.readAllBytes(state(future));
        ByteBuffer.wrap(bytes).putInt(4, 7);
        Files.write(state(future), bytes);
        Files.write(state(truncated), new byte[] {0});
        assertEquals(
                WorldEntry.Status.CORRUPTED,
                catalog.list().stream()
                        .filter(e -> e.id().equals(truncated.identity().id()))
                        .findFirst()
                        .orElseThrow()
                        .status());
        var statuses =
                catalog.list().stream().collect(java.util.stream.Collectors.toMap(WorldEntry::id, WorldEntry::status));
        assertEquals(WorldEntry.Status.READY, statuses.get(good.identity().id()));
        assertEquals(
                WorldEntry.Status.INCOMPLETE, statuses.get(missing.identity().id()));
        assertEquals(
                WorldEntry.Status.CORRUPTED, statuses.get(corrupt.identity().id()));
        assertEquals(
                WorldEntry.Status.UNSUPPORTED_VERSION,
                statuses.get(future.identity().id()));
        assertFalse(Files.exists(state(missing)));
        assertArrayEquals(new byte[] {0}, Files.readAllBytes(folder(corrupt).resolve("world.meta")));
    }

    @Test
    void distinguishesUnsupportedProfilesFromMismatchedDefinitions() throws Exception {
        var catalog = catalog();
        var metadata = metadata(1, "A", 42);
        catalog.create(metadata);
        for (var identity : List.of(
                new WorldIdentity(metadata.identity().id(), 43, "default", 11),
                new WorldIdentity(metadata.identity().id(), 42, "other", 11),
                new WorldIdentity(metadata.identity().id(), 42, "default", 12),
                new WorldIdentity(UUID.randomUUID(), 42, "default", 11))) {
            WorldMetadataCodec.write(
                    folder(metadata).resolve("world.meta"), new WorldMetadata(identity, "A", Instant.EPOCH));
            assertEquals(WorldEntry.Status.CORRUPTED, catalog.list().getFirst().status());
        }
        var future = new WorldMetadata(new WorldIdentity(new UUID(0, 2), 42, "default", 12), "Future", Instant.EPOCH);
        new WorldCatalog(directory, "default", 12).create(future);
        assertEquals(
                WorldEntry.Status.UNSUPPORTED_VERSION,
                catalog.list().stream()
                        .filter(e -> e.id().equals(future.identity().id()))
                        .findFirst()
                        .orElseThrow()
                        .status());
    }

    @Test
    void listingOnlyChecksHeadersAndDoesNotReadOrRepairPayload() throws Exception {
        var metadata = metadata(1, "A", 1);
        catalog().create(metadata);
        byte[] bytes = Files.readAllBytes(state(metadata));
        bytes[bytes.length - 1] ^= 1;
        Files.write(state(metadata), bytes);
        assertEquals(WorldEntry.Status.READY, catalog().list().getFirst().status());
        assertThrows(IOException.class, () -> WorldStateCodec.read(state(metadata), metadata.identity()));
        assertArrayEquals(bytes, Files.readAllBytes(state(metadata)));
    }

    @Test
    void failedPublicationNeverListsPartialWorldAndCleansItsOwnStaging() throws Exception {
        var metadata = metadata(1, "A", 1);
        assertThrows(
                AtomicMoveNotSupportedException.class,
                () -> catalog().create(metadata, (temporary, target) -> {
                    assertEquals(List.of(), catalog().list());
                    assertEquals(metadata, WorldMetadataCodec.read(temporary.resolve("world.meta")));
                    assertEquals(
                            WorldSnapshot.initial(metadata.identity(), metadata.createdAt()),
                            WorldStateCodec.read(temporary.resolve("state.vxl"), metadata.identity()));
                    throw new AtomicMoveNotSupportedException(temporary.toString(), target.toString(), "injected");
                }));
        assertEquals(List.of(), catalog().list());
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void creationNeverOverwritesExistingWorldOrAbandonedCreation() throws Exception {
        var metadata = metadata(1, "A", 1);
        catalog().create(metadata);
        byte[] committed = Files.readAllBytes(state(metadata));
        assertThrows(FileAlreadyExistsException.class, () -> catalog().create(metadata));
        assertArrayEquals(committed, Files.readAllBytes(state(metadata)));
        Path abandoned = directory.resolve(".creating-" + new UUID(0, 2));
        Files.createDirectory(abandoned);
        Path marker = abandoned.resolve("owned-by-another-operation");
        Files.write(marker, new byte[] {9});
        assertThrows(FileAlreadyExistsException.class, () -> catalog().create(metadata(2, "B", 2)));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(marker));
        assertEquals(1, catalog().list().size());
    }

    @Test
    void sameUuidCannotBePublishedTwiceDuringConcurrentCreation() throws Exception {
        var metadata = metadata(1, "A", 1);
        var staged = new CountDownLatch(1);
        var publish = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> catalog().create(metadata, (temporary, target) -> {
                staged.countDown();
                try {
                    if (!publish.await(5, TimeUnit.SECONDS)) throw new IOException("Test publication timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            }));
            try {
                assertTrue(staged.await(5, TimeUnit.SECONDS));
                assertThrows(FileAlreadyExistsException.class, () -> catalog().create(metadata));
                assertEquals(List.of(), catalog().list());
            } finally {
                publish.countDown();
            }
            assertEquals(
                    WorldEntry.Status.READY, result.get(5, TimeUnit.SECONDS).status());
        }
        assertEquals(1, catalog().list().size());
    }

    @Test
    void unsupportedCreationDoesNotTouchFilesystem() throws Exception {
        Path root = directory.resolve("worlds");
        assertThrows(
                UnsupportedWorldFormatException.class,
                () -> new WorldCatalog(root, "default", 12).create(metadata(1, "A", 1)));
        assertFalse(Files.exists(root));
    }

    @Test
    void missingMetadataAndNonRegularFilesAreIsolatedEntries() throws Exception {
        var missing = metadata(1, "Missing metadata", 1);
        var nonRegular = metadata(2, "Not a file", 2);
        catalog().create(missing);
        catalog().create(nonRegular);
        Files.delete(folder(missing).resolve("world.meta"));
        Files.delete(state(nonRegular));
        Files.createDirectory(state(nonRegular));
        var entries = catalog().list();
        assertEquals(WorldEntry.Status.INCOMPLETE, entries.getFirst().status());
        assertEquals(missing.identity().id().toString(), entries.getFirst().name());
        assertTrue(entries.getFirst().metadata().isEmpty());
        assertEquals(WorldEntry.Status.CORRUPTED, entries.getLast().status());
    }

    @Test
    void processDeathBeforeAndAfterDirectoryPublicationNeverExposesPartialWorld() throws Exception {
        String classes = Path.of(WorldCatalog.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                + java.io.File.pathSeparator
                + Path.of(WorldCatalogTest.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
        for (String point : List.of("before", "after")) {
            Path root = directory.resolve(point);
            var process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java")
                                    .toString(),
                            "-cp",
                            classes,
                            CrashCreator.class.getName(),
                            root.toString(),
                            point)
                    .start();
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                assertEquals(
                        73,
                        process.exitValue(),
                        new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            var entries = new WorldCatalog(root, "default", 11).list();
            if (point.equals("before")) {
                assertEquals(List.of(), entries);
                assertTrue(Files.exists(root.resolve(".creating-" + new UUID(0, 1))));
            } else {
                assertEquals(1, entries.size());
                var definition = entries.getFirst().metadata().orElseThrow();
                assertEquals(
                        WorldSnapshot.initial(definition.identity(), definition.createdAt()),
                        WorldStateCodec.read(
                                root.resolve(definition.identity().id().toString())
                                        .resolve("state.vxl"),
                                definition.identity()));
            }
        }
    }

    public static final class CrashCreator {
        public static void main(String[] args) throws Exception {
            var metadata = new WorldMetadata(
                    new WorldIdentity(new UUID(0, 1), 42, "default", 11), "Crash test", Instant.EPOCH);
            new WorldCatalog(Path.of(args[0]), "default", 11).create(metadata, (temporary, target) -> {
                if (args[1].equals("before")) Runtime.getRuntime().halt(73);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                Runtime.getRuntime().halt(73);
            });
        }
    }

    private WorldCatalog catalog() {
        return new WorldCatalog(directory, "default", 11);
    }

    private static WorldMetadata metadata(long id, String name, long seed) {
        return new WorldMetadata(
                new WorldIdentity(new UUID(0, id), seed, "default", 11), name, Instant.ofEpochMilli(1));
    }

    private Path folder(WorldMetadata metadata) {
        return directory.resolve(metadata.identity().id().toString());
    }

    private Path state(WorldMetadata metadata) {
        return folder(metadata).resolve("state.vxl");
    }
}
