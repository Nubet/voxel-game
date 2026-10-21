package com.norbertfila.mob;

import com.norbertfila.combat.DamageSource;
import com.norbertfila.combat.Health;
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
    private final Health health;
    private int fireTicks, burnedTicks;

    Mob(long id, MobType type, SpawnZone zone, double x, double y, double z, float yaw) {
        this.id = id;
        this.type = type;
        this.zone = zone;
        this.x = previousX = x;
        this.y = previousY = y;
        this.z = previousZ = z;
        this.yaw = yaw;
        health = new Health(type.maxHealth());
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

    public float health() {
        return health.value();
    }

    public boolean dead() {
        return health.dead();
    }

    public boolean burning() {
        return fireTicks > 0;
    }

    public int fireTicks() {
        return fireTicks;
    }

    public int hurtTicks() {
        return health.hurtTicks();
    }

    /** Sets the mob on fire for at least the given time; a longer fire already burning is kept. */
    public void ignite(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Fire duration is negative");
        if (fireTicks == 0) burnedTicks = 0;
        fireTicks = Math.max(fireTicks, ticks);
    }

    public void extinguish() {
        fireTicks = 0;
    }

    /**
     * Burns for one tick and returns how long this fire has burned, counted apart from the remaining time so that
     * re-igniting a burning mob does not delay its damage.
     */
    public int burn() {
        if (fireTicks == 0) return 0;
        fireTicks--;
        return ++burnedTicks;
    }

    /** Returns false when the hit landed during the hurt cooldown and was ignored. */
    public boolean damage(DamageSource source, float amount) {
        return health.damage(source, amount);
    }

    void ageHurt() {
        health.tick();
    }

    public Aabb body() {
        return type.body(x, y, z);
    }

    ChunkPos chunk() {
        return Coordinates.worldToChunk(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    MobRenderState renderState() {
        return new MobRenderState(
                id,
                type,
                previousX,
                previousY,
                previousZ,
                x,
                y,
                z,
                yaw,
                burning(),
                health.hurtTicks() / (float) Health.HURT_TICKS);
    }
}
