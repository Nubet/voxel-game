package com.norbertfila.persistence.world;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.CRC32C;

public final class WorldMetadataCodec {
    private static final int MAGIC = 0x56584D31;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 512;

    private WorldMetadataCodec() {}

    public static void write(Path target, WorldMetadata metadata) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        var identity = metadata.identity();
        output.writeLong(identity.id().getMostSignificantBits());
        output.writeLong(identity.id().getLeastSignificantBits());
        output.writeLong(identity.seed());
        output.writeUTF(identity.generatorId());
        output.writeInt(identity.generatorVersion());
        byte[] name = metadata.name().getBytes(StandardCharsets.UTF_8);
        output.writeInt(name.length);
        output.write(name);
        output.writeLong(metadata.createdAt().toEpochMilli());
        var checksum = new CRC32C();
        checksum.update(bytes.toByteArray());
        output.writeInt((int) checksum.getValue());
        AtomicFile.replace(target, channel -> {
            var buffer = ByteBuffer.wrap(bytes.toByteArray());
            while (buffer.hasRemaining()) channel.write(buffer);
        });
    }

    public static WorldMetadata read(Path source) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(source)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length < 12 || bytes.length > MAX_BYTES) throw new IOException("Invalid world metadata length");
        var checksum = new CRC32C();
        checksum.update(bytes, 0, bytes.length - 4);
        var input = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        if (input.readInt() != MAGIC) throw new IOException("Invalid world metadata magic");
        if (input.readInt() != FORMAT_VERSION)
            throw new UnsupportedWorldFormatException("Unsupported world metadata format");
        if ((int) checksum.getValue() != ByteBuffer.wrap(bytes).getInt(bytes.length - 4))
            throw new IOException("World metadata checksum mismatch");
        try {
            UUID id = new UUID(input.readLong(), input.readLong());
            long seed = input.readLong();
            int generatorLength = input.readUnsignedShort();
            if (generatorLength < 1 || generatorLength > 64)
                throw new IOException("Invalid generator identifier length");
            byte[] generator = new byte[generatorLength];
            input.readFully(generator);
            var identity =
                    new WorldIdentity(id, seed, new String(generator, StandardCharsets.US_ASCII), input.readInt());
            int nameLength = input.readInt();
            if (nameLength < 1 || nameLength > 256) throw new IOException("Invalid world name length");
            byte[] nameBytes = new byte[nameLength];
            input.readFully(nameBytes);
            String name = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(nameBytes))
                    .toString();
            var metadata = new WorldMetadata(identity, name, Instant.ofEpochMilli(input.readLong()));
            if (input.read() != -1) throw new IOException("Trailing world metadata bytes");
            return metadata;
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            throw new IOException("Invalid world metadata", malformed);
        }
    }
}
