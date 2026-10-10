package com.norbertfila.protocol;

import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MessageCodec {
    public static final int MAX_PAYLOAD_BYTES = 1 << 20;
    private static final int MAGIC = 0x56584331;
    private static final int HEADER_BYTES = 13;

    private MessageCodec() {}

    public static byte[] encode(WorldCommand command) {
        try {
            ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
            try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
                writePayload(payload, command);
            }
            if (payloadBytes.size() > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Payload exceeds limit");
            ByteArrayOutputStream messageBytes = new ByteArrayOutputStream(HEADER_BYTES + payloadBytes.size());
            try (DataOutputStream message = new DataOutputStream(messageBytes)) {
                message.writeInt(MAGIC);
                message.writeInt(ProtocolVersion.CURRENT);
                message.writeByte(type(command));
                message.writeInt(payloadBytes.size());
                payloadBytes.writeTo(message);
            }
            return messageBytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot encode protocol message", impossible);
        }
    }

    public static WorldCommand decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < HEADER_BYTES || bytes.length > HEADER_BYTES + MAX_PAYLOAD_BYTES) {
            throw new IOException("Message size is outside the allowed range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid protocol magic");
            int version = input.readInt();
            if (!ProtocolVersion.isSupported(version)) throw new IOException("Unsupported protocol version");
            int type = input.readUnsignedByte();
            int payloadLength = input.readInt();
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES || payloadLength != input.available()) {
                throw new IOException("Invalid payload length");
            }
            byte[] payloadBytes = input.readNBytes(payloadLength);
            try (DataInputStream payload = new DataInputStream(new ByteArrayInputStream(payloadBytes))) {
                WorldCommand command = readPayload(payload, type, version);
                if (payload.available() != 0) throw new IOException("Trailing payload bytes");
                return command;
            }
        } catch (EOFException malformed) {
            throw new IOException("Truncated protocol message", malformed);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid protocol payload", malformed);
        }
    }

    private static void writePayload(DataOutputStream output, WorldCommand command) throws IOException {
        switch (command) {
            case WorldCommand.Handshake handshake -> output.writeInt(handshake.version());
            case WorldCommand.Seed seed -> {
                output.writeLong(seed.seed());
                output.writeInt(seed.generatorVersion());
            }
            case WorldCommand.PlayerPosition position -> {
                output.writeDouble(position.x());
                output.writeDouble(position.y());
                output.writeDouble(position.z());
            }
            case WorldCommand.BlockEdit edit -> {
                output.writeInt(edit.position().x());
                output.writeInt(edit.position().y());
                output.writeInt(edit.position().z());
                output.writeByte(edit.value());
            }
            case WorldCommand.ChunkDeltaUpdate update -> writeDelta(output, update.delta());
        }
    }

    private static WorldCommand readPayload(DataInputStream input, int type, int version) throws IOException {
        return switch (type) {
            case 1 -> {
                int handshakeVersion = input.readInt();
                if (handshakeVersion != version) throw new IOException("Handshake version mismatch");
                yield new WorldCommand.Handshake(handshakeVersion);
            }
            case 2 -> new WorldCommand.Seed(input.readLong(), input.readInt());
            case 3 -> new WorldCommand.PlayerPosition(input.readDouble(), input.readDouble(), input.readDouble());
            case 4 ->
                new WorldCommand.BlockEdit(
                        new BlockPos(input.readInt(), input.readInt(), input.readInt()), input.readByte());
            case 5 -> new WorldCommand.ChunkDeltaUpdate(readDelta(input));
            default -> throw new IOException("Unknown message type: " + type);
        };
    }

    private static void writeDelta(DataOutputStream output, ChunkDelta delta) throws IOException {
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

    private static ChunkDelta readDelta(DataInputStream input) throws IOException {
        ChunkPos position = new ChunkPos(input.readInt(), input.readInt(), input.readInt());
        int count = input.readInt();
        if (count <= 0 || count > 4096) throw new IOException("Invalid chunk delta count");
        List<ChunkDelta.BlockChange> changes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            changes.add(new ChunkDelta.BlockChange(
                    new BlockPos(input.readInt(), input.readInt(), input.readInt()), input.readByte()));
        }
        return new ChunkDelta(position, changes);
    }

    private static int type(WorldCommand command) {
        return switch (command) {
            case WorldCommand.Handshake ignored -> 1;
            case WorldCommand.Seed ignored -> 2;
            case WorldCommand.PlayerPosition ignored -> 3;
            case WorldCommand.BlockEdit ignored -> 4;
            case WorldCommand.ChunkDeltaUpdate ignored -> 5;
        };
    }
}
