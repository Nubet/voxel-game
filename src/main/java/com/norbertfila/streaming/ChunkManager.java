package com.norbertfila.streaming;

import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChunkManager {
    private final World world;
    private final WorldGenerator generator;
    private final ViewDemand viewDemand;
    private final int hysteresisRadius;

    public ChunkManager(World world, WorldGenerator generator, ViewDemand viewDemand) {
        this(world, generator, viewDemand, viewDemand.horizontalRadius() + 1);
    }

    public ChunkManager(World world, WorldGenerator generator, ViewDemand viewDemand, int unloadRadius) {
        this.world = Objects.requireNonNull(world);
        this.generator = Objects.requireNonNull(generator);
        this.viewDemand = Objects.requireNonNull(viewDemand);
        if (unloadRadius < viewDemand.horizontalRadius()) {
            throw new IllegalArgumentException("Unload radius must not be smaller than load radius");
        }
        this.hysteresisRadius = unloadRadius;
    }

    public UpdateResult update(ChunkPos center) {
        boolean changed = viewDemand.update(center);
        if (!changed)
            return new UpdateResult(
                    false, 0, viewDemand.center(), viewDemand.requested().size(), List.of());

        int generated = 0;
        for (ChunkPos position : viewDemand.requested()) {
            if (world.hasChunk(position)) continue;
            world.requestChunk(position);
            world.putChunk(
                    position, Chunk.fromBlocks(generator.generate(position).blocks()));
            generated++;
        }
        List<ChunkPos> evicted = new ArrayList<>();
        for (ChunkPos position : world.residentPositions()) {
            if (withinHysteresis(position, center)) continue;
            world.removeChunk(position);
            evicted.add(position);
        }
        return new UpdateResult(
                true, generated, viewDemand.center(), viewDemand.requested().size(), List.copyOf(evicted));
    }

    public List<ChunkPos> residentPositions() {
        return world.residentPositions();
    }

    public int residentCount() {
        return world.residentCount();
    }

    public int loadRadius() {
        return viewDemand.horizontalRadius();
    }

    public int unloadRadius() {
        return hysteresisRadius;
    }

    public World world() {
        return world;
    }

    private boolean withinHysteresis(ChunkPos position, ChunkPos center) {
        if (position.y() < viewDemand.minChunkY() || position.y() > viewDemand.maxChunkY()) return false;
        int dx = position.x() - center.x();
        int dz = position.z() - center.z();
        return viewDemand.shape() == ViewDemand.Shape.SQUARE
                ? Math.abs(dx) <= hysteresisRadius && Math.abs(dz) <= hysteresisRadius
                : (long) dx * dx + (long) dz * dz <= (long) hysteresisRadius * hysteresisRadius;
    }

    public record UpdateResult(
            boolean changed, int generated, ChunkPos center, int requested, List<ChunkPos> evicted) {}
}
