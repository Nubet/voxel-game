package com.norbertfila.persistence.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.items.ItemSnapshot;
import com.norbertfila.items.ItemState;
import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.vegetation.PlantGrowthSnapshot;
import com.norbertfila.vegetation.PlantGrowthState;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldStateCodecTest {
    private static final WorldIdentity IDENTITY =
            new WorldIdentity(UUID.fromString("a1111111-2222-4333-8444-555555555555"), Long.MIN_VALUE, "default", 11);
    private static final Instant SAVED_AT = Instant.parse("2026-10-14T12:34:56.789Z");
    private static final int HEADER_BYTES = 72;

    @TempDir
    Path directory;

    @Test
    void preservesTimeAndCycleRuleAndRejectsInvalidTimeEvenWithValidChecksum() throws Exception {
        var base = populated();
        Path file = directory.resolve("time.vxl");
        for (var time : List.of(new WorldTime(0, true), new WorldTime(54001, false), new WorldTime(71999, true))) {
            var snapshot = new WorldSnapshot(
                    base.identity(),
                    base.sequence(),
                    base.lastSavedAt(),
                    base.deltas(),
                    base.fluids(),
                    base.inventory(),
                    base.player(),
                    base.items(),
                    time);
            WorldStateCodec.write(file, snapshot);
            assertEquals(snapshot, WorldStateCodec.read(file, IDENTITY));
        }
        byte[] original = Files.readAllBytes(file);
        for (int invalid : new int[] {-1, 72000, Integer.MAX_VALUE}) {
            byte[] bytes = original.clone();
            ByteBuffer.wrap(bytes).putInt(bytes.length - 13, invalid);
            updatePayloadChecksum(bytes);
            Files.write(file, bytes);
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
        }
        byte[] bytes = original.clone();
        bytes[bytes.length - 9] = 2;
        updatePayloadChecksum(bytes);
        Files.write(file, bytes);
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
    }

    @Test
    void readsVersionThreeWithoutRewritingAndPublishesVersionFiveOnlyOnSave() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 11);
        var metadata = new WorldMetadata(IDENTITY, "Legacy", SAVED_AT);
        catalog.create(metadata);
        Path file = directory.resolve(IDENTITY.id().toString()).resolve("state.vxl");
        var expected = withPlayer(new SavedPlayerState(1, 40, 2, 0, 0, Mode.SURVIVAL, false));
        byte[] legacy = legacyVersionThree(expected);
        Files.write(file, legacy);
        assertEquals(WorldEntry.Status.READY, catalog.list().getFirst().status());
        assertEquals(expected, catalog.open(IDENTITY.id()).snapshot());
        assertArrayEquals(legacy, Files.readAllBytes(file));
        WorldStateCodec.write(file, expected);
        assertEquals(5, ByteBuffer.wrap(Files.readAllBytes(file)).getInt(4));
        assertEquals(expected, catalog.open(IDENTITY.id()).snapshot());
        byte[] corrupt = legacy.clone();
        corrupt[HEADER_BYTES + 5] ^= 1;
        Files.write(file, corrupt);
        assertThrows(IOException.class, () -> catalog.open(IDENTITY.id()));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
    }

    private static byte[] legacyVersionThree(WorldSnapshot snapshot) {
        int payloadBytes = 104 + 34;
        byte[] bytes = new byte[HEADER_BYTES + payloadBytes + 4];
        var buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(0x56585731).putInt(3);
        buffer.putLong(IDENTITY.id().getMostSignificantBits())
                .putLong(IDENTITY.id().getLeastSignificantBits());
        buffer.putLong(IDENTITY.seed())
                .put((byte) 7)
                .put(new byte[] {'d', 'e', 'f', 'a', 'u', 'l', 't'})
                .putInt(11);
        buffer.putLong(snapshot.sequence())
                .putLong(snapshot.lastSavedAt().toEpochMilli())
                .putLong(payloadBytes);
        buffer.putInt(0);
        buffer.putInt(0).putLong(0).putInt(0).put((byte) 0).put(new byte[74]);
        var player = snapshot.player().orElseThrow();
        buffer.put((byte) 1).putDouble(player.x()).putDouble(player.y()).putDouble(player.z());
        buffer.putFloat(player.yaw()).putFloat(player.pitch()).put((byte) 0).put((byte) 0);
        buffer.putLong(1).putInt(0);
        assertEquals(bytes.length - 4, buffer.position());
        updateHeaderChecksum(bytes);
        updatePayloadChecksum(bytes);
        return bytes;
    }

    @Test
    void readsVersionFourWithItsDayTimeAndNoPlantHistoryWithoutRewriting() throws Exception {
        var base = withPlayer(new SavedPlayerState(1, 40, 2, 0, 0, Mode.SURVIVAL, false));
        var time = new WorldTime(54001, false);
        var expected = new WorldSnapshot(
                base.identity(),
                base.sequence(),
                base.lastSavedAt(),
                base.deltas(),
                base.fluids(),
                base.inventory(),
                base.player(),
                base.items(),
                time);
        byte[] v3 = legacyVersionThree(base);
        byte[] v4 = Arrays.copyOf(v3, v3.length + 5);
        var buffer = ByteBuffer.wrap(v4);
        buffer.putInt(4, 4).putLong(HEADER_BYTES - 12, buffer.getLong(HEADER_BYTES - 12) + 5);
        buffer.position(v3.length - 4);
        buffer.putInt(time.tickOfDay()).put((byte) 0);
        updateHeaderChecksum(v4);
        updatePayloadChecksum(v4);
        Path file = directory.resolve("v4.vxl");
        Files.write(file, v4);
        assertEquals(expected, WorldStateCodec.read(file, IDENTITY));
        assertArrayEquals(v4, Files.readAllBytes(file));
    }

    @Test
    void preservesDormantPlantDeadlinesAndRejectsMalformedGrowthWithValidChecksums() throws Exception {
        var base = populated();
        var growth = new PlantGrowthSnapshot(List.of(
                new PlantGrowthState(new BlockPos(-17, 3, 2), BlockId.REED, 100, 200, 2),
                new PlantGrowthState(new BlockPos(17, 3, 2), BlockId.REED, 300, 100000, 0)));
        var expected = new WorldSnapshot(
                base.identity(),
                base.sequence(),
                base.lastSavedAt(),
                base.deltas(),
                base.fluids(),
                base.inventory(),
                base.player(),
                base.items(),
                base.time(),
                growth);
        Path file = directory.resolve("plants.vxl");
        WorldStateCodec.write(file, expected);
        assertEquals(expected, WorldStateCodec.read(file, IDENTITY));
        byte[] original = Files.readAllBytes(file);
        int start = original.length - 4 - 2 * 37;
        List<java.util.function.Consumer<ByteBuffer>> mutations = List.of(
                bytes -> bytes.putInt(start - 4, -1),
                bytes -> bytes.putInt(start - 4, Integer.MAX_VALUE),
                bytes -> bytes.putInt(start + 4, Integer.MAX_VALUE),
                bytes -> bytes.put(start + 12, BlockId.STONE),
                bytes -> bytes.putLong(start + 13, -1),
                bytes -> bytes.putLong(start + 13, 501),
                bytes -> bytes.putLong(start + 21, 100),
                bytes -> bytes.putLong(start + 29, -1),
                bytes -> bytes.putInt(start + 37, -17));
        for (var mutation : mutations) {
            byte[] bytes = original.clone();
            mutation.accept(ByteBuffer.wrap(bytes));
            updatePayloadChecksum(bytes);
            Files.write(file, bytes);
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
        }
    }

    @Test
    void roundTripPreservesFlyingDormantAndPartiallyPickedUpItemsAndRejectsMalformedEntities() throws Exception {
        var base = populated();
        var items = new ItemState(
                9,
                List.of(
                        new ItemSnapshot(3, BlockId.COBBLE, 7, 8.25, 12.5, -8.75, -1, 2.5, .5, 40, 19, false),
                        new ItemSnapshot(
                                8, BlockId.DIRT, 64, 30_000_000.125, 1.125, -30_000_000.5, 0, 0, 0, 0, 17999, true)));
        var snapshot = new WorldSnapshot(
                base.identity(),
                base.sequence(),
                base.lastSavedAt(),
                base.deltas(),
                base.fluids(),
                base.inventory(),
                base.player(),
                items);
        Path file = directory.resolve("items.vxl");
        WorldStateCodec.write(file, snapshot);
        assertEquals(snapshot, WorldStateCodec.read(file, IDENTITY));
        byte[] original = Files.readAllBytes(file);
        int start = original.length - 4 - 4 - 5 - 2 * 73;
        List<java.util.function.Consumer<ByteBuffer>> mutations = List.of(
                bytes -> bytes.putLong(start - 12, 1),
                bytes -> bytes.putInt(start - 4, 4097),
                bytes -> bytes.putLong(start + 73, 3),
                bytes -> bytes.putInt(start + 8, 256),
                bytes -> bytes.putInt(start + 12, 65),
                bytes -> bytes.putDouble(start + 16, Double.NaN),
                bytes -> bytes.putDouble(start + 40, Double.POSITIVE_INFINITY),
                bytes -> bytes.putInt(start + 64, -1),
                bytes -> bytes.putInt(start + 64, 41),
                bytes -> bytes.putInt(start + 68, 18000),
                bytes -> bytes.put(start + 72, (byte) 2));
        for (var mutation : mutations) {
            byte[] bytes = original.clone();
            mutation.accept(ByteBuffer.wrap(bytes));
            updatePayloadChecksum(bytes);
            Files.write(file, bytes);
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
        }
    }

    @Test
    void roundTripPreservesDeltasEveryWaterStateDeferredTasksInventoryAndPlayerPrecision() throws Exception {
        var expected = populated();
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, expected);
        assertEquals(expected, WorldStateCodec.read(file, IDENTITY));
        var header = WorldStateCodec.readHeader(file);
        assertEquals(IDENTITY, header.identity());
        assertEquals(42, header.sequence());
        assertEquals(SAVED_AT, header.lastSavedAt());
        assertEquals(Files.size(file) - HEADER_BYTES - 4, header.payloadBytes());
        assertEquals(0x56585731, ByteBuffer.wrap(Files.readAllBytes(file)).getInt());
        assertEquals(5, ByteBuffer.wrap(Files.readAllBytes(file)).getInt(4));
        Path copy = directory.resolve("copy.vxl");
        WorldStateCodec.write(copy, expected);
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(copy));
        Files.setLastModifiedTime(file, FileTime.fromMillis(0));
        assertEquals(SAVED_AT, WorldStateCodec.readHeader(file).lastSavedAt());
    }

    @Test
    void initialWorldHasExplicitlyAbsentPlayerAndDoesNotNeedTerrainGeneration() throws Exception {
        var initial = WorldSnapshot.initial(IDENTITY, SAVED_AT);
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, initial);
        assertEquals(initial, WorldStateCodec.read(file, IDENTITY));
        assertEquals(113, WorldStateCodec.readHeader(file).payloadBytes());
        assertEquals(HEADER_BYTES + 113 + 4, Files.size(file));
        assertTrue(initial.player().isEmpty());
        assertEquals(initial, WorldSnapshot.initial(IDENTITY, SAVED_AT.plusNanos(999_999)));
    }

    @Test
    void headerSupportsBoundedGeneratorIdentifiersWithoutReadingTheWorld() throws Exception {
        Path file = directory.resolve("state.vxl");
        for (String generator : List.of("a", "g".repeat(64))) {
            var identity = new WorldIdentity(IDENTITY.id(), Long.MAX_VALUE, generator, Integer.MAX_VALUE);
            var snapshot = WorldSnapshot.initial(identity, SAVED_AT);
            WorldStateCodec.write(file, snapshot);
            assertEquals(identity, WorldStateCodec.readHeader(file).identity());
            assertEquals(snapshot, WorldStateCodec.read(file, identity));
        }
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(IDENTITY.id(), 0, "g".repeat(65), 1));
    }

    @Test
    void allPlayerModesHaveStableWireIdsAndExactDoubleCoordinates() throws Exception {
        Path file = directory.resolve("state.vxl");
        for (Mode mode : Mode.values()) {
            var player = new SavedPlayerState(
                    30_000_000.125, -23.25, -30_000_000.5, 327.5f, -35, mode, mode == Mode.CREATIVE);
            var snapshot = withPlayer(player);
            WorldStateCodec.write(file, snapshot);
            assertEquals(snapshot, WorldStateCodec.read(file, IDENTITY));
            int wireMode = switch (mode) {
                case SURVIVAL -> 0;
                case CREATIVE -> 1;
                case SPECTATOR -> 2;
            };
            var bytes = Files.readAllBytes(file);
            assertEquals(wireMode, bytes[bytes.length - 27]);
        }
    }

    @Test
    void lightweightHeaderDoesNotClaimToValidatePayload() throws Exception {
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, populated());
        var header = WorldStateCodec.readHeader(file);
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 5] ^= 1;
        Files.write(file, bytes);
        assertEquals(header, WorldStateCodec.readHeader(file));
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
    }

    @Test
    void rejectsEverySingleByteCorruptionAndTruncation() throws Exception {
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, populated());
        byte[] original = Files.readAllBytes(file);
        for (int i = 0; i < original.length; i++) {
            byte[] corrupted = original.clone();
            corrupted[i] ^= 1;
            Files.write(file, corrupted);
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY), "byte " + i);
            Files.write(file, Arrays.copyOf(original, i));
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY), "length " + i);
        }
        Files.write(file, Arrays.copyOf(original, original.length + 1));
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
    }

    @Test
    void rejectsMalformedPayloadEvenWithCorrectChecksum() throws Exception {
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, withPlayer(new SavedPlayerState(1, 40, 2, 0, 0, Mode.CREATIVE, true)));
        byte[] original = Files.readAllBytes(file);
        int player = original.length - 4 - 4 - 5 - 12 - 34;
        int inventory = player - 1 - 75;
        List<java.util.function.Consumer<ByteBuffer>> mutations = List.of(
                bytes -> bytes.putInt(HEADER_BYTES, Integer.MAX_VALUE),
                bytes -> bytes.putInt(HEADER_BYTES, -1),
                bytes -> bytes.putLong(HEADER_BYTES + 4, -1),
                bytes -> bytes.putInt(HEADER_BYTES + 12, Integer.MAX_VALUE),
                bytes -> bytes.put(inventory, (byte) 9),
                bytes -> bytes.put(inventory + 1, (byte) 255),
                bytes -> bytes.put(inventory + 2, (byte) 65),
                bytes -> bytes.put(player - 1, (byte) 2),
                bytes -> bytes.putDouble(player, Double.NaN),
                bytes -> bytes.putDouble(player + 8, Double.POSITIVE_INFINITY),
                bytes -> bytes.putDouble(player + 16, Integer.MAX_VALUE + 1.0),
                bytes -> bytes.putFloat(player + 24, Float.NaN),
                bytes -> bytes.putFloat(player + 28, 91),
                bytes -> bytes.put(player + 32, (byte) 3),
                bytes -> bytes.put(player + 32, (byte) 0),
                bytes -> bytes.put(player + 33, (byte) 2));
        for (int i = 0; i < mutations.size(); i++) {
            byte[] malformed = original.clone();
            mutations.get(i).accept(ByteBuffer.wrap(malformed));
            updatePayloadChecksum(malformed);
            Files.write(file, malformed);
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY), "mutation " + i);
        }
    }

    @Test
    void validatesChunkOwnershipDuplicateDeltasAndFluidTasksOnDisk() throws Exception {
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, populated());
        byte[] original = Files.readAllBytes(file);
        // Entries are chunk-local, so ownership cannot be violated; malformed entries must still be rejected.
        for (int badEntry : new int[] {1 << 28, 0xFF, 4095 << 16 | BlockId.STONE}) {
            byte[] malformed = original.clone();
            ByteBuffer.wrap(malformed).putInt(HEADER_BYTES + 20, badEntry);
            updatePayloadChecksum(malformed);
            Files.write(file, malformed);
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
        }

        int firstChanges = 9;
        int secondDelta = HEADER_BYTES + 4 + 16 + 4 * firstChanges;
        byte[] badFluid = original.clone();
        int fluidStart = secondDelta + 16 + 4;
        ByteBuffer.wrap(badFluid).putLong(fluidStart + 12 + 12, 16);
        updatePayloadChecksum(badFluid);
        Files.write(file, badFluid);
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));

        byte[] duplicateChunk = original.clone();
        var buffer = ByteBuffer.wrap(duplicateChunk);
        buffer.putInt(secondDelta, -1).putInt(secondDelta + 4, 0).putInt(secondDelta + 8, 0);
        buffer.putInt(secondDelta + 16, -1).putInt(secondDelta + 20, 1).putInt(secondDelta + 24, 1);
        updatePayloadChecksum(duplicateChunk);
        Files.write(file, duplicateChunk);
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));

        byte[] duplicateTask = original.clone();
        System.arraycopy(duplicateTask, fluidStart + 12, duplicateTask, fluidStart + 12 + 24, 12);
        updatePayloadChecksum(duplicateTask);
        Files.write(file, duplicateTask);
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));

        byte[] duplicateBlock = original.clone();
        System.arraycopy(duplicateBlock, HEADER_BYTES + 20, duplicateBlock, HEADER_BYTES + 20 + 4, 4);
        updatePayloadChecksum(duplicateBlock);
        Files.write(file, duplicateBlock);
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
    }

    @Test
    void rejectsWrongIdentityBeforePublishingAnyLoadedStateWithoutChangingFile() throws Exception {
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, populated());
        byte[] original = Files.readAllBytes(file);
        for (var wrong : List.of(
                new WorldIdentity(UUID.randomUUID(), IDENTITY.seed(), "default", 11),
                new WorldIdentity(IDENTITY.id(), 0, "default", 11),
                new WorldIdentity(IDENTITY.id(), IDENTITY.seed(), "other", 11),
                new WorldIdentity(IDENTITY.id(), IDENTITY.seed(), "default", 12))) {
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, wrong));
            assertThrows(
                    IOException.class, () -> WorldStateCodec.readHeader(file).requireIdentity(wrong));
            assertArrayEquals(original, Files.readAllBytes(file));
        }
    }

    @Test
    void rejectsOldAndFutureFormatsMalformedHeadersAndOversizedFiles() throws Exception {
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, populated());
        byte[] original = Files.readAllBytes(file);
        for (int[] mutation : List.of(
                new int[] {0, 0x56584C31},
                new int[] {4, 0},
                new int[] {4, 1},
                new int[] {4, 2},
                new int[] {4, 6},
                new int[] {4, Integer.MAX_VALUE})) {
            byte[] bytes = original.clone();
            ByteBuffer.wrap(bytes).putInt(mutation[0], mutation[1]);
            Files.write(file, bytes);
            assertThrows(IOException.class, () -> WorldStateCodec.readHeader(file));
            assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
        }
        for (long length : new long[] {-1, 0, Long.MAX_VALUE}) {
            byte[] bytes = original.clone();
            ByteBuffer.wrap(bytes).putLong(HEADER_BYTES - 12, length);
            updateHeaderChecksum(bytes);
            Files.write(file, bytes);
            assertThrows(IOException.class, () -> WorldStateCodec.readHeader(file));
        }
        Files.write(file, original);
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.position(64L * 1024 * 1024);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
        assertThrows(IOException.class, () -> WorldStateCodec.readHeader(file));
        assertThrows(IOException.class, () -> WorldStateCodec.read(file, IDENTITY));
    }

    @Test
    void missingOrUnreadableSaveNeverCreatesAReplacement() throws Exception {
        Path missing = directory.resolve("missing.vxl");
        assertThrows(IOException.class, () -> WorldStateCodec.read(missing, IDENTITY));
        assertThrows(IOException.class, () -> WorldStateCodec.readHeader(missing));
        assertFalse(Files.exists(missing));
        Path corrupt = directory.resolve("corrupt.vxl");
        byte[] bytes = {1, 2, 3};
        Files.write(corrupt, bytes);
        assertThrows(IOException.class, () -> WorldStateCodec.read(corrupt, IDENTITY));
        assertArrayEquals(bytes, Files.readAllBytes(corrupt));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(corrupt), files.toList());
        }
    }

    @Test
    void replacesWholeSnapshotsWithoutRetainingPreviousWorldState() throws Exception {
        Path file = directory.resolve("state.vxl");
        WorldStateCodec.write(file, populated());
        var next = withPlayer(new SavedPlayerState(-.125, 1.5, -.25, -90, 90, Mode.SURVIVAL, false));
        WorldStateCodec.write(file, next);
        assertEquals(next, WorldStateCodec.read(file, IDENTITY));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void snapshotOwnsImmutableCollectionsAndRejectsAmbiguousInitialState() {
        var changes = new ArrayList<>(populated().deltas());
        var snapshot = new WorldSnapshot(
                IDENTITY,
                42,
                SAVED_AT,
                changes,
                FluidSnapshot.EMPTY,
                InventorySnapshot.EMPTY,
                populated().player());
        changes.clear();
        assertEquals(2, snapshot.deltas().size());
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.deltas().clear());
        var delta = snapshot.deltas().getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldSnapshot(
                        IDENTITY,
                        1,
                        SAVED_AT,
                        List.of(delta, delta),
                        FluidSnapshot.EMPTY,
                        InventorySnapshot.EMPTY,
                        snapshot.player()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldSnapshot(
                        IDENTITY,
                        1,
                        SAVED_AT,
                        List.of(),
                        FluidSnapshot.EMPTY,
                        InventorySnapshot.EMPTY,
                        Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldSnapshot(
                        IDENTITY,
                        0,
                        SAVED_AT,
                        List.of(delta),
                        FluidSnapshot.EMPTY,
                        InventorySnapshot.EMPTY,
                        Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> WorldSnapshot.initial(IDENTITY, Instant.ofEpochMilli(-1)));
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(IDENTITY.id(), 0, "../default", 11));
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(IDENTITY.id(), 0, "default", 0));
        assertThrows(IllegalArgumentException.class, () -> new SavedPlayerState(0, 0, 0, 0, 0, Mode.SURVIVAL, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SavedPlayerState(Double.NaN, 0, 0, 0, 0, Mode.CREATIVE, false));
    }

    private static WorldSnapshot populated() {
        var changes = new ArrayList<ChunkDelta.BlockChange>();
        for (byte state = 0; state <= 8; state++)
            changes.add(new ChunkDelta.BlockChange(new BlockPos(-16 + state, 1, 1), BlockId.WATER, state));
        var edge = new ChunkDelta(
                new ChunkPos(-134217728, 0, 134217727),
                List.of(new ChunkDelta.BlockChange(
                        new BlockPos(Integer.MIN_VALUE, 0, Integer.MAX_VALUE), BlockId.POPPY)));
        var inventory = new Inventory();
        inventory.creativePick(8, BlockId.POPPY);
        inventory.select(8);
        inventory.click(8, true, false);
        var fluids = new FluidSnapshot(
                500,
                List.of(
                        new FluidSnapshot.Update(new BlockPos(-16, 1, 1), 3, List.of()),
                        new FluidSnapshot.Update(new BlockPos(-15, 1, 1), 15, List.of(new ChunkPos(-2, 0, 0)))));
        return new WorldSnapshot(
                IDENTITY,
                42,
                SAVED_AT,
                List.of(new ChunkDelta(new ChunkPos(-1, 0, 0), changes), edge),
                fluids,
                inventory.snapshot(),
                Optional.of(
                        new SavedPlayerState(30_000_000.125, -23.25, -30_000_000.5, 327.5f, -35, Mode.CREATIVE, true)));
    }

    private static WorldSnapshot withPlayer(SavedPlayerState player) {
        return new WorldSnapshot(
                IDENTITY,
                43,
                SAVED_AT.plusMillis(1),
                List.of(),
                FluidSnapshot.EMPTY,
                InventorySnapshot.EMPTY,
                Optional.of(player));
    }

    private static void updatePayloadChecksum(byte[] bytes) {
        var checksum = new CRC32C();
        checksum.update(bytes, HEADER_BYTES, bytes.length - HEADER_BYTES - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) checksum.getValue());
    }

    private static void updateHeaderChecksum(byte[] bytes) {
        var checksum = new CRC32C();
        checksum.update(bytes, 0, HEADER_BYTES - 4);
        ByteBuffer.wrap(bytes).putInt(HEADER_BYTES - 4, (int) checksum.getValue());
    }
}
