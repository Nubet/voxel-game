package com.norbertfila.persistence;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.lod.WorldChangeOverlay;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WaterState;
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
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_DELTAS = 1_000_000;
    private static final int MAX_CHANGES_PER_CHUNK = Chunk.BLOCK_BYTES;
    private static final long MAX_SAVE_BYTES = 64L * 1024 * 1024;
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z);

    private WorldSave() {}

    public static void save(Path target, long seed, int generatorVersion, World world) throws IOException {
        Objects.requireNonNull(target);
        Objects.requireNonNull(world);
        if (generatorVersion < 0) throw new IllegalArgumentException("Generator version must not be negative");
        save(target, capture(seed, generatorVersion, world));
    }

    public static Snapshot capture(long seed, int generatorVersion, World world) {
        return capture(seed, generatorVersion, world, FluidSnapshot.EMPTY);
    }

    public static Snapshot capture(long seed, int generatorVersion, World world, FluidSnapshot fluids) {
        Objects.requireNonNull(world);
        if (generatorVersion < 0) throw new IllegalArgumentException("Generator version must not be negative");
        return new Snapshot(
                seed,
                generatorVersion,
                SCHEMA_VERSION,
                chunkDeltas(world.changes().changes()),
                fluids);
    }

    public static void save(Path target, Snapshot snapshot) throws IOException {
        Objects.requireNonNull(target);
        Objects.requireNonNull(snapshot);
        List<ChunkDelta> deltas = snapshot.deltas();
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try {
            write(temporary, snapshot, deltas);
            if (Files.size(temporary) > MAX_SAVE_BYTES) throw new IOException("World save exceeds size limit");
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
        if (Files.size(source) > MAX_SAVE_BYTES) throw new IOException("World save exceeds size limit");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid world save header");
            int schemaVersion = input.readInt();
            if (schemaVersion != 1 && schemaVersion != SCHEMA_VERSION)
                throw new IOException("Unsupported world save schema");
            long seed = input.readLong();
            int generatorVersion = input.readInt();
            if (generatorVersion < 0) throw new IOException("Invalid generator version");
            int deltaCount = input.readInt();
            if (deltaCount < 0 || deltaCount > MAX_DELTAS) throw new IOException("Invalid delta count");
            List<ChunkDelta> deltas = new ArrayList<>(deltaCount);
            for (int index = 0; index < deltaCount; index++) deltas.add(readDelta(input, schemaVersion));
            FluidSnapshot fluids = schemaVersion == 1 ? FluidSnapshot.EMPTY : readFluids(input);
            if (input.read() != -1) throw new IOException("Trailing bytes in world save");
            return new Loaded(seed, generatorVersion, schemaVersion, List.copyOf(deltas), fluids);
        } catch (EOFException malformed) {
            throw new IOException("Truncated world save", malformed);
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            throw new IOException("Invalid world save", malformed);
        }
    }

    public static void restore(World world, Loaded save, int farTileSize) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(save);
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        for (ChunkDelta delta : save.deltas()) {
            for (ChunkDelta.BlockChange change : delta.changes())
                world.setBlock(change.position(), change.value(), change.waterState(), farTileSize);
        }
    }

    private static void write(Path path, Snapshot snapshot, List<ChunkDelta> deltas) throws IOException {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            output.writeInt(MAGIC);
            output.writeInt(SCHEMA_VERSION);
            output.writeLong(snapshot.seed());
            output.writeInt(snapshot.generatorVersion());
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
                    output.writeByte(change.waterState());
                }
            }
            output.writeLong(snapshot.fluids().tick());
            output.writeInt(snapshot.fluids().updates().size());
            for (var update : snapshot.fluids().updates()) {
                output.writeInt(update.position().x());
                output.writeInt(update.position().y());
                output.writeInt(update.position().z());
                output.writeLong(update.remainingTicks());
                output.writeInt(update.missingChunks().size());
                for (ChunkPos missing : update.missingChunks()) {
                    output.writeInt(missing.x());
                    output.writeInt(missing.y());
                    output.writeInt(missing.z());
                }
            }
        }
    }

    private static FluidSnapshot readFluids(DataInputStream input) throws IOException {
        long tick = input.readLong();
        int count = input.readInt();
        if (count < 0 || count > FluidSnapshot.MAX_UPDATES) throw new IOException("Invalid fluid update count");
        var updates = new ArrayList<FluidSnapshot.Update>();
        for (int index = 0; index < count; index++) {
            BlockPos position = new BlockPos(input.readInt(), input.readInt(), input.readInt());
            long remaining = input.readLong();
            int missingCount = input.readInt();
            if (missingCount < 0 || missingCount > 16) throw new IOException("Invalid missing chunk count");
            var missing = new ArrayList<ChunkPos>();
            for (int m = 0; m < missingCount; m++)
                missing.add(new ChunkPos(input.readInt(), input.readInt(), input.readInt()));
            updates.add(new FluidSnapshot.Update(position, remaining, missing));
        }
        return new FluidSnapshot(tick, updates);
    }

    private static ChunkDelta readDelta(DataInputStream input, int schemaVersion) throws IOException {
        ChunkPos position = new ChunkPos(input.readInt(), input.readInt(), input.readInt());
        int changeCount = input.readInt();
        if (changeCount <= 0 || changeCount > MAX_CHANGES_PER_CHUNK) throw new IOException("Invalid chunk delta size");
        List<ChunkDelta.BlockChange> changes = new ArrayList<>(changeCount);
        for (int index = 0; index < changeCount; index++) {
            BlockPos block = new BlockPos(input.readInt(), input.readInt(), input.readInt());
            byte value = input.readByte();
            byte state = schemaVersion == 1 ? WaterState.defaultFor(value) : input.readByte();
            changes.add(new ChunkDelta.BlockChange(block, value, state));
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
                    .add(new ChunkDelta.BlockChange(change.position(), change.value(), change.waterState()));
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(CHUNK_ORDER))
                .map(entry -> new ChunkDelta(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record Loaded(
            long seed, int generatorVersion, int schemaVersion, List<ChunkDelta> deltas, FluidSnapshot fluids) {
        public Loaded {
            if (generatorVersion < 0 || (schemaVersion != 1 && schemaVersion != SCHEMA_VERSION))
                throw new IllegalArgumentException("Unsupported world save metadata");
            Objects.requireNonNull(fluids);
            Objects.requireNonNull(deltas);
            deltas = List.copyOf(deltas);
        }
    }

    public record Snapshot(
            long seed, int generatorVersion, int schemaVersion, List<ChunkDelta> deltas, FluidSnapshot fluids) {
        public Snapshot {
            if (generatorVersion < 0 || schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Invalid world save metadata");
            }
            Objects.requireNonNull(deltas);
            Objects.requireNonNull(fluids);
            if (deltas.size() > MAX_DELTAS) throw new IllegalArgumentException("Too many chunk deltas");
            deltas = List.copyOf(deltas);
        }
    }
}
