package com.norbertfila.server;

import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HeadlessWorldRunner {
    private final long ownerThreadId = Thread.currentThread().threadId();
    private final World world;
    private final ChunkManager chunks;
    private final WorldEdit edits;
    private long tick;

    public HeadlessWorldRunner(WorldGenerator generator, ViewDemand demand, int farTileSize) {
        this(new World(), generator, demand, farTileSize);
    }

    public HeadlessWorldRunner(World world, WorldGenerator generator, ViewDemand demand, int farTileSize) {
        this.world = Objects.requireNonNull(world);
        this.chunks = new ChunkManager(world, Objects.requireNonNull(generator), Objects.requireNonNull(demand));
        this.edits = new WorldEdit(world, farTileSize);
    }

    public TickResult tick(ChunkPos center, List<? extends Command> commands) {
        assertOwner();
        Objects.requireNonNull(center);
        Objects.requireNonNull(commands);
        ChunkManager.UpdateResult update = chunks.update(center);
        List<ChunkPos> dirty = new ArrayList<>();
        int changed = 0;
        for (Command command : commands) {
            Objects.requireNonNull(command);
            if (command instanceof SetBlock setBlock) {
                WorldEdit.Result result = edits.setBlock(setBlock.position(), setBlock.value());
                if (result.changed()) {
                    changed++;
                    dirty.addAll(result.dirtyChunks());
                }
            }
        }
        tick++;
        return new TickResult(tick, update.generated(), changed, List.copyOf(dirty), world.residentCount());
    }

    public World world() {
        assertOwner();
        return world;
    }

    public long tickCount() {
        assertOwner();
        return tick;
    }

    private void assertOwner() {
        if (Thread.currentThread().threadId() != ownerThreadId) {
            throw new IllegalStateException("HeadlessWorldRunner is single-thread owned");
        }
    }

    public sealed interface Command permits SetBlock {}

    public record SetBlock(BlockPos position, byte value) implements Command {
        public SetBlock {
            Objects.requireNonNull(position);
            if (!BlockId.isValid(value)) throw new IllegalArgumentException("Unknown block id");
        }
    }

    public record TickResult(
            long tick, int generatedChunks, int changedBlocks, List<ChunkPos> dirtyChunks, int residentChunks) {
        public TickResult {
            if (tick <= 0 || generatedChunks < 0 || changedBlocks < 0 || residentChunks < 0) {
                throw new IllegalArgumentException("Invalid tick result");
            }
            dirtyChunks = List.copyOf(dirtyChunks);
        }
    }
}
