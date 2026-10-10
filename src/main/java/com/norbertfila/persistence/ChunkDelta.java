package com.norbertfila.persistence;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ChunkDelta(ChunkPos position, List<BlockChange> changes) {
    public ChunkDelta {
        Objects.requireNonNull(position);
        Objects.requireNonNull(changes);
        if (changes.isEmpty() || changes.size() > Chunk.BLOCK_BYTES) {
            throw new IllegalArgumentException("Chunk delta size is outside the allowed range");
        }
        changes = List.copyOf(changes);
        Set<BlockPos> positions = new HashSet<>();
        for (BlockChange change : changes) {
            if (!BlockId.isValid(change.value())) throw new IllegalArgumentException("Unknown block id");
            if (!positions.add(change.position())) throw new IllegalArgumentException("Duplicate delta block");
            if (!position.equals(computeChunk(change.position()))) {
                throw new IllegalArgumentException("Delta block does not belong to its chunk");
            }
        }
    }

    private static ChunkPos computeChunk(BlockPos position) {
        return new ChunkPos(
                Math.floorDiv(position.x(), Chunk.SIZE),
                Math.floorDiv(position.y(), Chunk.SIZE),
                Math.floorDiv(position.z(), Chunk.SIZE));
    }

    public record BlockChange(BlockPos position, byte value) {
        public BlockChange {
            Objects.requireNonNull(position);
        }
    }
}
