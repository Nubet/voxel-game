package com.norbertfila.protocol;

import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.WaterState;
import java.util.Objects;

public sealed interface WorldCommand
        permits WorldCommand.Handshake,
                WorldCommand.Seed,
                WorldCommand.PlayerPosition,
                WorldCommand.BlockEdit,
                WorldCommand.ChunkDeltaUpdate {
    record Handshake(int version) implements WorldCommand {
        public Handshake {
            if (!ProtocolVersion.isSupported(version))
                throw new IllegalArgumentException("Unsupported protocol version");
        }
    }

    record Seed(long seed, int generatorVersion) implements WorldCommand {
        public Seed {
            if (generatorVersion < 0) throw new IllegalArgumentException("Generator version must not be negative");
        }
    }

    record PlayerPosition(double x, double y, double z) implements WorldCommand {
        public PlayerPosition {
            if (!finite(x) || !finite(y) || !finite(z)) throw new IllegalArgumentException("Position must be finite");
        }
    }

    record BlockEdit(BlockPos position, byte value, byte waterState) implements WorldCommand {
        public BlockEdit(BlockPos position, byte value) {
            this(position, value, WaterState.defaultFor(value));
        }

        public BlockEdit {
            Objects.requireNonNull(position);
            if (!BlockId.isValid(value)) throw new IllegalArgumentException("Unknown block id");
            WaterState.validate(value, waterState);
        }
    }

    record ChunkDeltaUpdate(ChunkDelta delta) implements WorldCommand {
        public ChunkDeltaUpdate {
            Objects.requireNonNull(delta);
        }
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
