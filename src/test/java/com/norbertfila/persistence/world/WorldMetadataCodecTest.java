package com.norbertfila.persistence.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldMetadataCodecTest {
    @TempDir
    Path directory;

    @Test
    void preservesUtf8IdentityAndCreationTimeWithCanonicalNames() throws Exception {
        Path file = directory.resolve("world.meta");
        var identity = new WorldIdentity(UUID.randomUUID(), Long.MIN_VALUE, "default", 11);
        for (String name : List.of("  Łąka żółwia  ", "🌍".repeat(64), "../świat")) {
            var metadata = new WorldMetadata(identity, name, Instant.parse("2026-10-14T12:34:56.123456789Z"));
            WorldMetadataCodec.write(file, metadata);
            assertEquals(metadata, WorldMetadataCodec.read(file));
            assertEquals(name.strip(), metadata.name());
            assertEquals(Instant.parse("2026-10-14T12:34:56.123Z"), metadata.createdAt());
            assertTrue(Files.size(file) <= 512);
        }
    }

    @Test
    void rejectsBlankOverlongControlAndUnpairedSurrogateNamesBeforeWriting() {
        var identity = new WorldIdentity(UUID.randomUUID(), 0, "default", 11);
        for (String name : List.of(
                "", " \t ", "a".repeat(65), "🌍".repeat(65), "a\nb", "a\u0000b", "a\uD800b", "a\u2028b", "a\u2029b"))
            assertThrows(IllegalArgumentException.class, () -> new WorldMetadata(identity, name, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new WorldMetadata(identity, "A", Instant.ofEpochMilli(-1)));
    }

    @Test
    void rejectsEveryByteFlipTruncationAndTrailingByte() throws Exception {
        Path file = directory.resolve("world.meta");
        WorldMetadataCodec.write(
                file,
                new WorldMetadata(new WorldIdentity(UUID.randomUUID(), 42, "default", 11), "Łąka", Instant.EPOCH));
        byte[] original = Files.readAllBytes(file);
        for (int i = 0; i < original.length; i++) {
            byte[] corrupt = original.clone();
            corrupt[i] ^= 1;
            Files.write(file, corrupt);
            assertThrows(IOException.class, () -> WorldMetadataCodec.read(file), "byte " + i);
            Files.write(file, Arrays.copyOf(original, i));
            assertThrows(IOException.class, () -> WorldMetadataCodec.read(file), "length " + i);
        }
        Files.write(file, Arrays.copyOf(original, original.length + 1));
        assertThrows(IOException.class, () -> WorldMetadataCodec.read(file));
        Files.write(file, new byte[513]);
        assertThrows(IOException.class, () -> WorldMetadataCodec.read(file));
    }

    @Test
    void rejectsMalformedUtf8AndLengthsEvenWithCorrectChecksum() throws Exception {
        Path file = directory.resolve("world.meta");
        WorldMetadataCodec.write(
                file,
                new WorldMetadata(new WorldIdentity(UUID.randomUUID(), 0, "default", 11), "World", Instant.EPOCH));
        byte[] original = Files.readAllBytes(file);
        int nameLengthOffset = 32 + 2 + 7 + 4;
        for (int length : new int[] {-1, 0, 257, Integer.MAX_VALUE}) {
            byte[] malformed = original.clone();
            ByteBuffer.wrap(malformed).putInt(nameLengthOffset, length);
            updateChecksum(malformed);
            Files.write(file, malformed);
            assertThrows(IOException.class, () -> WorldMetadataCodec.read(file));
        }
        byte[] malformed = original.clone();
        malformed[nameLengthOffset + 4] = (byte) 0xFF;
        updateChecksum(malformed);
        Files.write(file, malformed);
        assertThrows(IOException.class, () -> WorldMetadataCodec.read(file));
        malformed = original.clone();
        ByteBuffer.wrap(malformed).putShort(32, (short) 65535);
        updateChecksum(malformed);
        Files.write(file, malformed);
        assertThrows(IOException.class, () -> WorldMetadataCodec.read(file));
    }

    @Test
    void unsupportedVersionIsTypedForCatalogClassification() throws Exception {
        Path file = directory.resolve("world.meta");
        WorldMetadataCodec.write(
                file, new WorldMetadata(new WorldIdentity(UUID.randomUUID(), 0, "default", 11), "A", Instant.EPOCH));
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer.wrap(bytes).putInt(4, 2);
        Files.write(file, bytes);
        assertThrows(UnsupportedWorldFormatException.class, () -> WorldMetadataCodec.read(file));
    }

    private static void updateChecksum(byte[] bytes) {
        var checksum = new CRC32C();
        checksum.update(bytes, 0, bytes.length - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) checksum.getValue());
    }
}
