package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldGenerator;
import java.util.List;
import java.util.Objects;

public final class ChunkManager {
    private final World world;
    private final WorldGenerator generator;
    private final ViewDemand viewDemand;

    public ChunkManager(World world, WorldGenerator generator, ViewDemand viewDemand) {
        this.world = Objects.requireNonNull(world);
        this.generator = Objects.requireNonNull(generator);
        this.viewDemand = Objects.requireNonNull(viewDemand);
    }

    public UpdateResult update(ChunkPos center) {
        boolean changed = viewDemand.update(center);
        if (!changed)
            return new UpdateResult(
                    false, 0, viewDemand.center(), viewDemand.requested().size());

        int generated = 0;
        for (ChunkPos position : viewDemand.requested()) {
            if (world.hasChunk(position)) continue;
            world.requestChunk(position);
            world.putChunk(position, generator.generate(position.x(), position.y(), position.z()));
            generated++;
        }
        return new UpdateResult(
                true, generated, viewDemand.center(), viewDemand.requested().size());
    }

    public List<ChunkPos> residentPositions() {
        return world.residentPositions();
    }

    public record UpdateResult(boolean changed, int generated, ChunkPos center, int requested) {}
}
