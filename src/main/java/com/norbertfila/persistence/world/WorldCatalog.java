package com.norbertfila.persistence.world;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WorldCatalog {
    private final Path root;
    private final String generatorId;
    private final int generatorVersion;

    public WorldCatalog(Path root, String generatorId, int generatorVersion) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        this.generatorId = Objects.requireNonNull(generatorId);
        if (!generatorId.matches("[a-z][a-z0-9_-]{0,63}") || generatorVersion < 1)
            throw new IllegalArgumentException("Invalid supported generator profile");
        this.generatorVersion = generatorVersion;
    }

    public WorldEntry create(WorldMetadata metadata) throws IOException {
        return create(metadata, (temporary, target) -> Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE));
    }

    WorldEntry create(WorldMetadata metadata, AtomicFile.Commit publish) throws IOException {
        Objects.requireNonNull(metadata);
        if (!supported(metadata.identity())) throw new UnsupportedWorldFormatException("Unsupported generator profile");
        Files.createDirectories(root);
        Path destination = root.resolve(metadata.identity().id().toString());
        Path temporary = root.resolve(".creating-" + metadata.identity().id());
        Files.createDirectory(temporary);
        try {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
                throw new FileAlreadyExistsException(destination.toString());
            Path meta = temporary.resolve("world.meta"), state = temporary.resolve("state.vxl");
            WorldMetadataCodec.write(meta, metadata);
            var initial = WorldSnapshot.initial(metadata.identity(), metadata.createdAt());
            WorldStateCodec.write(state, initial);
            if (!WorldMetadataCodec.read(meta).equals(metadata)
                    || !WorldStateCodec.read(state, metadata.identity()).equals(initial))
                throw new IOException("Incomplete initial world");
            var header = WorldStateCodec.readHeader(state);
            publish.move(temporary, destination);
            return new WorldEntry(
                    metadata.identity().id(), Optional.of(metadata), Optional.of(header), WorldEntry.Status.READY, "");
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary.resolve("world.meta"));
                Files.deleteIfExists(temporary.resolve("state.vxl"));
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public List<WorldEntry> list() throws IOException {
        var entries = new ArrayList<WorldEntry>();
        try (var directories = Files.newDirectoryStream(root)) {
            for (Path directory : directories) {
                UUID id;
                try {
                    String name = directory.getFileName().toString();
                    id = UUID.fromString(name);
                    if (!id.toString().equals(name) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                        continue;
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                entries.add(inspect(directory, id));
            }
        } catch (DirectoryIteratorException iteration) {
            throw iteration.getCause();
        } catch (NoSuchFileException missing) {
            if (!Files.notExists(root)) throw missing;
        }
        entries.sort(Comparator.comparing(WorldEntry::lastSavedAt).reversed().thenComparing(WorldEntry::id));
        return List.copyOf(entries);
    }

    public record Loaded(WorldMetadata metadata, WorldSnapshot snapshot, Path statePath) {}

    public Loaded open(UUID id) throws IOException {
        Path directory = root.resolve(Objects.requireNonNull(id).toString());
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing world directory");
        Path meta = directory.resolve("world.meta"), state = directory.resolve("state.vxl");
        requireRegularFile(meta);
        requireRegularFile(state);
        var metadata = WorldMetadataCodec.read(meta);
        if (!metadata.identity().id().equals(id)) throw new IOException("World directory UUID mismatch");
        if (!supported(metadata.identity())) throw new UnsupportedWorldFormatException("Unsupported generator profile");
        return new Loaded(metadata, WorldStateCodec.read(state, metadata.identity()), state);
    }

    private WorldEntry inspect(Path directory, UUID id) {
        Optional<WorldMetadata> metadata = Optional.empty();
        Optional<WorldStateCodec.Header> header = Optional.empty();
        try {
            Path meta = directory.resolve("world.meta"), state = directory.resolve("state.vxl");
            requireRegularFile(meta);
            metadata = Optional.of(WorldMetadataCodec.read(meta));
            if (!metadata.orElseThrow().identity().id().equals(id))
                throw new IOException("World directory UUID mismatch");
            requireRegularFile(state);
            header = Optional.of(WorldStateCodec.readHeader(state));
            header.orElseThrow().requireIdentity(metadata.orElseThrow().identity());
            if (!supported(metadata.orElseThrow().identity()))
                throw new UnsupportedWorldFormatException("Unsupported generator profile");
            // READY is a catalog/header check; opening still requires full payload validation.
            return new WorldEntry(id, metadata, header, WorldEntry.Status.READY, "");
        } catch (UnsupportedWorldFormatException unsupported) {
            return new WorldEntry(
                    id, metadata, header, WorldEntry.Status.UNSUPPORTED_VERSION, unsupported.getMessage());
        } catch (NoSuchFileException missing) {
            return new WorldEntry(
                    id, metadata, header, WorldEntry.Status.INCOMPLETE, "Missing world metadata or state");
        } catch (FileSystemException denied) {
            return new WorldEntry(id, metadata, header, WorldEntry.Status.UNREADABLE, "World files cannot be read");
        } catch (IOException malformed) {
            return new WorldEntry(
                    id,
                    metadata,
                    header,
                    WorldEntry.Status.CORRUPTED,
                    Objects.toString(malformed.getMessage(), "Invalid or truncated world data"));
        }
    }

    private boolean supported(WorldIdentity identity) {
        return generatorId.equals(identity.generatorId()) && generatorVersion == identity.generatorVersion();
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                .isRegularFile()) throw new IOException("Expected a regular world file");
    }
}
