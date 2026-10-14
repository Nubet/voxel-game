package com.norbertfila.persistence;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.persistence.world.*;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public final class TestWorlds {
    public static WorldSnapshot capture(long seed, int version, World world) {
        return capture(seed, version, world, FluidSnapshot.EMPTY, InventorySnapshot.EMPTY);
    }

    public static WorldSnapshot capture(long seed, int version, World world, FluidSnapshot fluids) {
        return capture(seed, version, world, fluids, InventorySnapshot.EMPTY);
    }

    public static WorldSnapshot capture(
            long seed, int version, World world, FluidSnapshot fluids, InventorySnapshot inventory) {
        return WorldSnapshots.capture(
                identity(seed, version),
                1,
                world,
                fluids,
                inventory,
                new SavedPlayerState(.5, 1, .5, -90, -25, Mode.SURVIVAL, false));
    }

    public static WorldIdentity identity(long seed, int version) {
        return new WorldIdentity(new UUID(version, seed), seed, "default", version);
    }

    public static Path prepare(Path path, long seed, int version) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            save(path, WorldSnapshot.initial(identity(seed, version), Instant.EPOCH));
        }
        return path;
    }

    public static void save(Path path, long seed, int version, World world) throws IOException {
        save(path, capture(seed, version, world));
    }

    public static void save(Path path, WorldSnapshot snapshot) throws IOException {
        WorldStateCodec.write(path, snapshot);
    }

    public static WorldSnapshot load(Path path) throws IOException {
        return WorldStateCodec.read(path, WorldStateCodec.readHeader(path).identity());
    }

    public static void restore(World world, WorldSnapshot snapshot, int farTileSize) {
        WorldSnapshots.restore(world, snapshot, farTileSize);
    }
}
