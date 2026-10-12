package com.norbertfila.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.io.IOException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageCodecTest {
    @Test
    void blockEditHasStableGoldenBytes() throws Exception {
        WorldCommand command = new WorldCommand.BlockEdit(new BlockPos(-1, 2, 3), BlockId.GRASS);
        byte[] expected = HexFormat.of().parseHex("5658433100000002040000000EFFFFFFFF000000020000000301FF");

        assertArrayEquals(expected, MessageCodec.encode(command));
        assertEquals(command, MessageCodec.decode(expected));
    }

    @Test
    void roundTripsEveryMessageType() throws Exception {
        List<WorldCommand> commands = List.of(
                new WorldCommand.Handshake(ProtocolVersion.CURRENT),
                new WorldCommand.Seed(42, 3),
                new WorldCommand.PlayerPosition(1.5, -2.0, 3.25),
                new WorldCommand.BlockEdit(new BlockPos(1, 2, 3), BlockId.STONE),
                new WorldCommand.ChunkDeltaUpdate(new ChunkDelta(
                        new ChunkPos(0, 0, 0),
                        List.of(new ChunkDelta.BlockChange(new BlockPos(1, 2, 3), BlockId.DIRT)))));

        for (WorldCommand command : commands) assertEquals(command, MessageCodec.decode(MessageCodec.encode(command)));
    }

    @Test
    void roundTripsWaterEditsAndChunkDeltas() throws Exception {
        BlockPos position = new BlockPos(-17, -1, -9);
        for (WorldCommand command : List.of(
                new WorldCommand.BlockEdit(position, BlockId.WATER),
                new WorldCommand.ChunkDeltaUpdate(new ChunkDelta(
                        new ChunkPos(-2, -1, -1), List.of(new ChunkDelta.BlockChange(position, BlockId.WATER)))))) {
            assertEquals(command, MessageCodec.decode(MessageCodec.encode(command)));
        }
    }

    @Test
    void rejectsTruncatedUnknownAndTrailingMessages() {
        byte[] valid = MessageCodec.encode(new WorldCommand.Handshake(ProtocolVersion.CURRENT));

        assertThrows(IOException.class, () -> MessageCodec.decode(new byte[] {1, 2, 3}));
        byte[] unknown = valid.clone();
        unknown[8] = 99;
        assertThrows(IOException.class, () -> MessageCodec.decode(unknown));
        byte[] trailing = new byte[valid.length + 1];
        System.arraycopy(valid, 0, trailing, 0, valid.length);
        assertThrows(IOException.class, () -> MessageCodec.decode(trailing));
    }

    @Test
    void allWaterStatesRoundTripAndInvalidPairsAreRejected() throws Exception {
        BlockPos position = new BlockPos(-17, -1, -9);
        for (byte state = 0; state <= 8; state++) {
            for (WorldCommand command : List.of(
                    new WorldCommand.BlockEdit(position, BlockId.WATER, state),
                    new WorldCommand.ChunkDeltaUpdate(new ChunkDelta(
                            new ChunkPos(-2, -1, -1),
                            List.of(new ChunkDelta.BlockChange(position, BlockId.WATER, state))))))
                assertEquals(command, MessageCodec.decode(MessageCodec.encode(command)));
        }
        byte[] invalid = MessageCodec.encode(new WorldCommand.BlockEdit(position, BlockId.STONE));
        invalid[invalid.length - 1] = 1;
        assertThrows(IOException.class, () -> MessageCodec.decode(invalid));
        byte[] legacy = HexFormat.of().parseHex("5658433100000001040000000DFFFFFFFF000000020000000301");
        assertThrows(IOException.class, () -> MessageCodec.decode(legacy));
    }
}
