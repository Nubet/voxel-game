package com.norbertfila.persistence;

import com.norbertfila.lod.WorldChangeOverlay;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WorldSave {
    private static final int MAGIC = 0x56584C31;
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_DELTAS = 1_000_000;
    private static final int MAX_CHANGES_PER_CHUNK = Chunk.BLOCK_BYTES;
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z);

    private WorldSave() {}

    public static void save(Path target, long seed, int generatorVersion, World world) throws IOException {
        Objects.requireNonNull(target);
        Objects.requireNonNull(world);
        if (generatorVersion < 0) throw new IllegalArgumentException("Generator version must not be negative");
        List<ChunkDelta> deltas = chunkDeltas(world.changes().changes());
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try {
            write(temporary, seed, generatorVersion, deltas);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Loaded load(Path source) throws IOException {
        Objects.requireNonNull(source);
        if (Files.size(source) > 64L * 1024 * 1024) throw new IOException("World save exceeds size limit");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid world save header");
            int schemaVersion = input.readInt();
            if (schemaVersion != SCHEMA_VERSION) throw new IOException("Unsupported world save schema");
            long seed = input.readLong();
            int generatorVersion = input.readInt();
            int deltaCount = input.readInt();
            if (deltaCount < 0 || deltaCount > MAX_DELTAS) throw new IOException("Invalid delta count");
            List<ChunkDelta> deltas = new ArrayList<>(deltaCount);
            for (int index = 0; index < deltaCount; index++) deltas.add(readDelta(input));
            if (input.read() != -1) throw new IOException("Trailing bytes in world save");
            return new Loaded(seed, generatorVersion, schemaVersion, List.copyOf(deltas));
        } catch (EOFException malformed) {
            throw new IOException("Truncated world save", malformed);
        }
    }

    public static void restore(World world, Loaded save, int farTileSize) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(save);
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        for (ChunkDelta delta : save.deltas()) {
            for (ChunkDelta.BlockChange change : delta.changes())
                world.setBlock(change.position(), change.value(), farTileSize);
        }
    }

    private static void write(Path path, long seed, int generatorVersion, List<ChunkDelta> deltas) throws IOException {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            output.writeInt(MAGIC);
            output.writeInt(SCHEMA_VERSION);
            output.writeLong(seed);
            output.writeInt(generatorVersion);
            output.writeInt(deltas.size());
            for (ChunkDelta delta : deltas) {
                output.writeInt(delta.position().x());
                output.writeInt(delta.position().y());
                output.writeInt(delta.position().z());
                output.writeInt(delta.changes().size());
                for (ChunkDelta.BlockChange change : delta.changes()) {
                    output.writeInt(change.position().x());
                    output.writeInt(change.position().y());
                    output.writeInt(change.position().z());
                    output.writeByte(change.value());
                }
            }
        }
    }

    private static ChunkDelta readDelta(DataInputStream input) throws IOException {
        ChunkPos position = new ChunkPos(input.readInt(), input.readInt(), input.readInt());
        int changeCount = input.readInt();
        if (changeCount <= 0 || changeCount > MAX_CHANGES_PER_CHUNK) throw new IOException("Invalid chunk delta size");
        List<ChunkDelta.BlockChange> changes = new ArrayList<>(changeCount);
        for (int index = 0; index < changeCount; index++) {
            changes.add(new ChunkDelta.BlockChange(
                    new BlockPos(input.readInt(), input.readInt(), input.readInt()), input.readByte()));
        }
        try {
            return new ChunkDelta(position, changes);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid chunk delta", invalid);
        }
    }

    private static List<ChunkDelta> chunkDeltas(List<WorldChangeOverlay.Change> changes) {
        Map<ChunkPos, List<ChunkDelta.BlockChange>> grouped = new LinkedHashMap<>();
        for (WorldChangeOverlay.Change change : changes) {
            ChunkPos chunk = new ChunkPos(
                    Math.floorDiv(change.position().x(), Chunk.SIZE),
                    Math.floorDiv(change.position().y(), Chunk.SIZE),
                    Math.floorDiv(change.position().z(), Chunk.SIZE));
            grouped.computeIfAbsent(chunk, ignored -> new ArrayList<>())
                    .add(new ChunkDelta.BlockChange(change.position(), change.value()));
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(CHUNK_ORDER))
                .map(entry -> new ChunkDelta(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record Loaded(long seed, int generatorVersion, int schemaVersion, List<ChunkDelta> deltas) {
        public Loaded {
            if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported world save schema");
            Objects.requireNonNull(deltas);
            deltas = List.copyOf(deltas);
        }
    }
}
