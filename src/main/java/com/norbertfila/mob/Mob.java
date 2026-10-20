package com.norbertfila.mob;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;

/** A living mob; its position is the center of its feet. */
public final class Mob {
    private final long id;
    private final MobType type;
    private final SpawnZone zone;
    private final float yaw;
    double x, y, z, previousX, previousY, previousZ, verticalVelocity;
    boolean onGround;
    int ticksAwayFromPlayers;

    Mob(long id, MobType type, SpawnZone zone, double x, double y, double z, float yaw) {
        this.id = id;
        this.type = type;
        this.zone = zone;
        this.x = previousX = x;
        this.y = previousY = y;
        this.z = previousZ = z;
        this.yaw = yaw;
    }

    public long id() {
        return id;
    }

    public MobType type() {
        return type;
    }

    /** Zone the mob spawned in; it keeps counting against that zone's cap wherever it goes. */
    public SpawnZone zone() {
        return zone;
    }

    public PopulationKey populationKey() {
        return new PopulationKey(type.category(), zone);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public boolean onGround() {
        return onGround;
    }

    public int ticksAwayFromPlayers() {
        return ticksAwayFromPlayers;
    }

    public void markNearPlayer() {
        ticksAwayFromPlayers = 0;
    }

    public Aabb body() {
        return type.body(x, y, z);
    }

    ChunkPos chunk() {
        return Coordinates.worldToChunk(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    MobRenderState renderState() {
        return new MobRenderState(id, type, previousX, previousY, previousZ, x, y, z, yaw);
    }
}
