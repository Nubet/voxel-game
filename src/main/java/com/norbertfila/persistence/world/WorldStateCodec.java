package com.norbertfila.persistence.world;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32C;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

public final class WorldStateCodec {
    private static final int MAGIC = 0x56585731;
    // Version 2 stores block changes as one packed int each; version 1 files are reported as unsupported.
    private static final int FORMAT_VERSION = 2;
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final int FIXED_HEADER_BYTES = 65;
    private static final int PLAYER_BYTES = 34;
    private static final int MIN_PAYLOAD_BYTES = 92;

    private WorldStateCodec() {}

    public record Header(WorldIdentity identity, long sequence, Instant lastSavedAt, long payloadBytes) {
        public Header {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(lastSavedAt);
            if (sequence < 0
                    || lastSavedAt.isBefore(Instant.EPOCH)
                    || payloadBytes < MIN_PAYLOAD_BYTES
                    || fileBytes(identity, payloadBytes) > MAX_FILE_BYTES)
                throw new IllegalArgumentException("Invalid world state header");
            lastSavedAt = Instant.ofEpochMilli(lastSavedAt.toEpochMilli());
        }

        public void requireIdentity(WorldIdentity expected) throws IOException {
            if (!identity.equals(Objects.requireNonNull(expected)))
                throw new IOException("World state identity does not match its definition");
        }
    }

    public static void write(Path target, WorldSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot);
        Header header =
                new Header(snapshot.identity(), snapshot.sequence(), snapshot.lastSavedAt(), payloadBytes(snapshot));
        AtomicFile.replace(target, channel -> {
            var output = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
            byte[] headerBytes = encodeHeader(header);
            output.write(headerBytes);
            output.writeInt(checksum(headerBytes));
            var checksum = new CRC32C();
            var payload = new DataOutputStream(new CheckedOutputStream(output, checksum));
            writePayload(payload, snapshot);
            output.writeInt((int) checksum.getValue());
            output.flush();
        });
    }

    // This validates the header and file length, not the payload's integrity.
    public static Header readHeader(Path source) throws IOException {
        try (var channel = FileChannel.open(source)) {
            var input = new DataInputStream(
                    new BufferedInputStream(Channels.newInputStream(channel), FIXED_HEADER_BYTES + 64));
            return readHeader(input, channel.size());
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            throw new IOException("Invalid world state header", malformed);
        }
    }

    public static WorldSnapshot read(Path source, WorldIdentity expected) throws IOException {
        Objects.requireNonNull(expected);
        try (var channel = FileChannel.open(source)) {
            var input = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));
            Header header = readHeader(input, channel.size());
            header.requireIdentity(expected);
            var checksum = new CRC32C();
            var payload = new DataInputStream(new CheckedInputStream(input, checksum));
            WorldSnapshot snapshot = readPayload(payload, header);
            if (payloadBytes(snapshot) != header.payloadBytes() || input.readInt() != (int) checksum.getValue())
                throw new IOException("World state payload length/checksum mismatch");
            if (input.read() != -1) throw new IOException("Trailing world state bytes");
            return snapshot;
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            throw new IOException("Invalid world state payload", malformed);
        }
    }

    private static byte[] encodeHeader(Header header) throws IOException {
        var bytes = new ByteArrayOutputStream(128);
        var output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        var identity = header.identity();
        output.writeLong(identity.id().getMostSignificantBits());
        output.writeLong(identity.id().getLeastSignificantBits());
        output.writeLong(identity.seed());
        byte[] generator = identity.generatorId().getBytes(StandardCharsets.US_ASCII);
        output.writeByte(generator.length);
        output.write(generator);
        output.writeInt(identity.generatorVersion());
        output.writeLong(header.sequence());
        output.writeLong(header.lastSavedAt().toEpochMilli());
        output.writeLong(header.payloadBytes());
        return bytes.toByteArray();
    }

    private static Header readHeader(DataInputStream input, long fileBytes) throws IOException {
        if (fileBytes > MAX_FILE_BYTES) throw new IOException("World state exceeds size limit");
        if (input.readInt() != MAGIC) throw new IOException("Invalid world state magic");
        if (input.readInt() != FORMAT_VERSION)
            throw new UnsupportedWorldFormatException("Unsupported world state format");
        UUID id = new UUID(input.readLong(), input.readLong());
        long seed = input.readLong();
        int length = input.readUnsignedByte();
        if (length < 1 || length > 64) throw new IOException("Invalid generator identifier length");
        byte[] generator = new byte[length];
        input.readFully(generator);
        var identity = new WorldIdentity(id, seed, new String(generator, StandardCharsets.US_ASCII), input.readInt());
        var header = new Header(identity, input.readLong(), Instant.ofEpochMilli(input.readLong()), input.readLong());
        if (input.readInt() != checksum(encodeHeader(header)))
            throw new IOException("World state header checksum mismatch");
        if (fileBytes != fileBytes(identity, header.payloadBytes()))
            throw new IOException("World state length mismatch");
        return header;
    }

    private static long fileBytes(WorldIdentity identity, long payloadBytes) {
        return Math.addExact((long) FIXED_HEADER_BYTES + identity.generatorId().length() + Integer.BYTES, payloadBytes);
    }

    private static int checksum(byte[] bytes) {
        var checksum = new CRC32C();
        checksum.update(bytes);
        return (int) checksum.getValue();
    }

    private static long payloadBytes(WorldSnapshot snapshot) {
        long bytes = MIN_PAYLOAD_BYTES + (snapshot.player().isPresent() ? PLAYER_BYTES : 0);
        for (var delta : snapshot.deltas()) bytes += 16L + 4L * delta.size();
        for (var update : snapshot.fluids().updates())
            bytes += 24L + 12L * update.missingChunks().size();
        return bytes;
    }

    private static void writePayload(DataOutputStream output, WorldSnapshot snapshot) throws IOException {
        output.writeInt(snapshot.deltas().size());
        for (var delta : snapshot.deltas()) {
            writeChunk(output, delta.position());
            output.writeInt(delta.size());
            for (int index = 0; index < delta.size(); index++) output.writeInt(delta.entry(index));
        }
        output.writeLong(snapshot.fluids().tick());
        output.writeInt(snapshot.fluids().updates().size());
        for (var update : snapshot.fluids().updates()) {
            writeBlock(output, update.position());
            output.writeLong(update.remainingTicks());
            output.writeInt(update.missingChunks().size());
            for (var chunk : update.missingChunks()) writeChunk(output, chunk);
        }
        output.writeByte(snapshot.inventory().selectedSlot());
        for (int slot = 0; slot < 37; slot++) {
            output.writeByte(snapshot.inventory().block(slot));
            output.writeByte(snapshot.inventory().count(slot));
        }
        output.writeBoolean(snapshot.player().isPresent());
        if (snapshot.player().isPresent()) {
            var player = snapshot.player().orElseThrow();
            output.writeDouble(player.x());
            output.writeDouble(player.y());
            output.writeDouble(player.z());
            output.writeFloat(player.yaw());
            output.writeFloat(player.pitch());
            output.writeByte(
                    switch (player.mode()) {
                        case SURVIVAL -> 0;
                        case CREATIVE -> 1;
                        case SPECTATOR -> 2;
                    });
            output.writeBoolean(player.flying());
        }
    }

    private static WorldSnapshot readPayload(DataInputStream input, Header header) throws IOException {
        int deltaCount = count(input, (int) (header.payloadBytes() / 30));
        var deltas = new ArrayList<ChunkDelta>();
        for (int d = 0; d < deltaCount; d++) {
            ChunkPos position = readChunk(input);
            int changeCount = count(input, Chunk.BLOCK_BYTES);
            if (changeCount == 0) throw new IOException("Empty chunk delta");
            int[] entries = new int[changeCount];
            for (int c = 0; c < changeCount; c++) entries[c] = input.readInt();
            deltas.add(ChunkDelta.packed(position, entries));
        }
        long tick = input.readLong();
        int updateCount = count(input, Math.min(FluidSnapshot.MAX_UPDATES, (int) (header.payloadBytes() / 24)));
        var updates = new ArrayList<FluidSnapshot.Update>();
        for (int u = 0; u < updateCount; u++) {
            BlockPos position = readBlock(input);
            long delay = input.readLong();
            int missingCount = count(input, 16);
            var missing = new ArrayList<ChunkPos>();
            for (int m = 0; m < missingCount; m++) missing.add(readChunk(input));
            updates.add(new FluidSnapshot.Update(position, delay, missing));
        }
        var fluids = new FluidSnapshot(tick, updates);
        int selected = input.readUnsignedByte();
        byte[] blocks = new byte[37];
        int[] counts = new int[37];
        for (int slot = 0; slot < 37; slot++) {
            blocks[slot] = input.readByte();
            counts[slot] = input.readUnsignedByte();
        }
        var inventory = new InventorySnapshot(blocks, counts, selected);
        Optional<SavedPlayerState> player = Optional.empty();
        if (readBoolean(input)) {
            double x = input.readDouble(), y = input.readDouble(), z = input.readDouble();
            float yaw = input.readFloat(), pitch = input.readFloat();
            Mode mode = switch (input.readUnsignedByte()) {
                case 0 -> Mode.SURVIVAL;
                case 1 -> Mode.CREATIVE;
                case 2 -> Mode.SPECTATOR;
                default -> throw new IOException("Unknown saved player mode");
            };
            player = Optional.of(new SavedPlayerState(x, y, z, yaw, pitch, mode, readBoolean(input)));
        }
        return new WorldSnapshot(
                header.identity(), header.sequence(), header.lastSavedAt(), deltas, fluids, inventory, player);
    }

    private static int count(DataInputStream input, int maximum) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) throw new IOException("World state collection exceeds bounds");
        return count;
    }

    private static boolean readBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid world state boolean");
        return value == 1;
    }

    private static void writeBlock(DataOutputStream output, BlockPos position) throws IOException {
        output.writeInt(position.x());
        output.writeInt(position.y());
        output.writeInt(position.z());
    }

    private static void writeChunk(DataOutputStream output, ChunkPos position) throws IOException {
        output.writeInt(position.x());
        output.writeInt(position.y());
        output.writeInt(position.z());
    }

    private static BlockPos readBlock(DataInputStream input) throws IOException {
        return new BlockPos(input.readInt(), input.readInt(), input.readInt());
    }

    private static ChunkPos readChunk(DataInputStream input) throws IOException {
        return new ChunkPos(input.readInt(), input.readInt(), input.readInt());
    }
}
