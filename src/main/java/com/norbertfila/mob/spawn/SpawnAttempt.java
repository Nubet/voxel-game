package com.norbertfila.mob.spawn;

import com.norbertfila.light.LightLevels;
import com.norbertfila.mob.PopulationKey;
import com.norbertfila.mob.SpawnZone;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockPos;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** One candidate block for one mob; light and zone are looked up lazily because they are the costly part. */
public final class SpawnAttempt {
    private final SpawnContext context;
    private final SpawnEntry entry;
    private final BlockPos position;
    private OptionalInt skyLight, blockLight;

    public SpawnAttempt(SpawnContext context, SpawnEntry entry, BlockPos position) {
        this.context = Objects.requireNonNull(context);
        this.entry = Objects.requireNonNull(entry);
        this.position = Objects.requireNonNull(position);
    }

    public SpawnContext context() {
        return context;
    }

    public SpawnEntry entry() {
        return entry;
    }

    public BlockPos position() {
        return position;
    }

    public double x() {
        return position.x() + .5;
    }

    public double y() {
        return position.y();
    }

    public double z() {
        return position.z() + .5;
    }

    public Aabb body() {
        return entry.type().body(x(), y(), z());
    }

    public OptionalInt skyLight() {
        if (skyLight == null) skyLight = light().skyLight(position);
        return skyLight;
    }

    public OptionalInt blockLight() {
        if (blockLight == null) blockLight = light().blockLight(position);
        return blockLight;
    }

    /** Positions reached by sky light count as surface, everything sealed from the sky as cave. */
    public Optional<SpawnZone> zone() {
        var sky = skyLight();
        if (sky.isEmpty()) return Optional.empty();
        return Optional.of(sky.getAsInt() > 0 ? SpawnZone.SURFACE : SpawnZone.CAVE);
    }

    public Optional<PopulationKey> populationKey() {
        return zone().map(zone -> new PopulationKey(entry.type().category(), zone));
    }

    private LightLevels light() {
        return context.environment().light();
    }
}
