package com.norbertfila.vegetation;

import com.norbertfila.world.BlockPos;
import java.util.HashSet;
import java.util.List;

public record PlantGrowthSnapshot(List<PlantGrowthState> plants) {
    public static final PlantGrowthSnapshot EMPTY = new PlantGrowthSnapshot(List.of());
    public static final int MAX_PLANTS = 1_000_000;

    public PlantGrowthSnapshot {
        plants = List.copyOf(plants);
        if (plants.size() > MAX_PLANTS) throw new IllegalArgumentException("Too many growing plants");
        var roots = new HashSet<BlockPos>();
        for (var plant : plants)
            if (!roots.add(plant.root())) throw new IllegalArgumentException("Duplicate plant root");
    }
}
