package com.norbertfila.persistence.world;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.items.ItemState;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.vegetation.PlantGrowthSnapshot;
import com.norbertfila.weather.WeatherState;
import com.norbertfila.weather.WeatherSystem;
import com.norbertfila.world.*;
import java.time.Instant;
import java.util.*;

public final class WorldSnapshots {
    private WorldSnapshots() {}

    public static WorldSnapshot capture(
            WorldIdentity identity,
            long sequence,
            World world,
            FluidSnapshot fluids,
            InventorySnapshot inventory,
            SavedPlayerState player) {
        return capture(identity, sequence, world, fluids, inventory, player, ItemState.EMPTY);
    }

    public static WorldSnapshot capture(
            WorldIdentity identity,
            long sequence,
            World world,
            FluidSnapshot fluids,
            InventorySnapshot inventory,
            SavedPlayerState player,
            ItemState items) {
        return capture(identity, sequence, world, fluids, inventory, player, items, WorldTime.INITIAL);
    }

    public static WorldSnapshot capture(
            WorldIdentity identity,
            long sequence,
            World world,
            FluidSnapshot fluids,
            InventorySnapshot inventory,
            SavedPlayerState player,
            ItemState items,
            WorldTime time) {
        return capture(identity, sequence, world, fluids, inventory, player, items, time, PlantGrowthSnapshot.EMPTY);
    }

    public static WorldSnapshot capture(
            WorldIdentity identity,
            long sequence,
            World world,
            FluidSnapshot fluids,
            InventorySnapshot inventory,
            SavedPlayerState player,
            ItemState items,
            WorldTime time,
            PlantGrowthSnapshot growth) {
        return capture(
                identity,
                sequence,
                world,
                fluids,
                inventory,
                player,
                items,
                time,
                growth,
                new WeatherSystem(identity.seed(), fluids.tick()).snapshot());
    }

    public static WorldSnapshot capture(
            WorldIdentity identity,
            long sequence,
            World world,
            FluidSnapshot fluids,
            InventorySnapshot inventory,
            SavedPlayerState player,
            ItemState items,
            WorldTime time,
            PlantGrowthSnapshot growth,
            WeatherState weather) {
        var deltas = world.changes().deltas();
        return new WorldSnapshot(
                identity,
                sequence,
                Instant.now(),
                deltas,
                fluids,
                inventory,
                Optional.of(player),
                items,
                time,
                growth,
                weather);
    }

    public static void restore(World world, WorldSnapshot snapshot, int farTileSize) {
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        for (var delta : snapshot.deltas())
            for (int index = 0; index < delta.size(); index++)
                world.setBlock(delta.blockPosition(index), delta.value(index), delta.waterState(index), farTileSize);
    }
}
