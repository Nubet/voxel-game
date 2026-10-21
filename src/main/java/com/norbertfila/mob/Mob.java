package com.norbertfila.mob;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.combat.Health;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import java.util.Objects;

/**
 * A living mob; its position is the center of its feet. AI only states intents (walk, jump, look) and
 * {@link MobPhysics} turns them into motion on the next tick.
 */
public final class Mob implements AttackTarget {
    /** Body turn rate in degrees per tick; Minecraft turns mobs by up to 30 degrees per 20 TPS tick. */
    static final float MAX_TURN = 10;

    private final long id;
    private final MobType type;
    private final SpawnZone zone;
    private final Health health;
    double x, y, z, previousX, previousY, previousZ;
    double velocityX, velocityY, velocityZ;
    float yaw, previousYaw;
    double walkDistance, previousWalkDistance;
    boolean onGround, horizontalCollision, inWater;
    int ticksAwayFromPlayers;
    double moveX, moveZ, moveSpeed;
    boolean jumpRequested;
    private double lookX, lookZ;
    private boolean looking;
    private MobAction action = MobAction.NONE;
    private float actionProgress;
    private int fireTicks, burnedTicks;

    Mob(long id, MobType type, SpawnZone zone, double x, double y, double z, float yaw) {
        this.id = id;
        this.type = type;
        this.zone = zone;
        this.x = previousX = x;
        this.y = previousY = y;
        this.z = previousZ = z;
        this.yaw = previousYaw = yaw;
        health = new Health(type.attributes().maxHealth());
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

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double z() {
        return z;
    }

    @Override
    public double eyeY() {
        return y + type.eyeHeight();
    }

    @Override
    public double eyeHeight() {
        return type.eyeHeight();
    }

    @Override
    public boolean attackable() {
        return !dead();
    }

    /** Damage with a push in blocks per second, lifting the mob like Minecraft knockback. */
    @Override
    public boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ) {
        var hit = health.damage(source, amount);
        if (hit == Health.Hit.FRESH && (knockbackX != 0 || knockbackZ != 0))
            knockback(knockbackX, KNOCKBACK_LIFT, knockbackZ);
        return hit.landed();
    }

    /** Removes the mob on the next hazard pass, as when a creeper is consumed by its own blast. */
    public void kill() {
        health.kill();
    }

    public float yaw() {
        return yaw;
    }

    public boolean onGround() {
        return onGround;
    }

    public boolean horizontalCollision() {
        return horizontalCollision;
    }

    public boolean inWater() {
        return inWater;
    }

    public double horizontalSpeed() {
        return Math.hypot(velocityX, velocityZ);
    }

    public int ticksAwayFromPlayers() {
        return ticksAwayFromPlayers;
    }

    public void markNearPlayer() {
        ticksAwayFromPlayers = 0;
    }

    /** Walks toward a point at the given multiple of the type's walk speed until told otherwise. */
    public void walkToward(double targetX, double targetZ, double speedFactor) {
        double dx = targetX - x, dz = targetZ - z, distance = Math.hypot(dx, dz);
        if (distance < 1e-4) {
            stop();
            return;
        }
        walk(dx / distance, dz / distance, speedFactor);
    }

    /** Walks along a unit direction, independent of where the mob looks, which lets it strafe or back away. */
    public void walk(double directionX, double directionZ, double speedFactor) {
        if (!Double.isFinite(directionX) || !Double.isFinite(directionZ) || !(speedFactor >= 0))
            throw new IllegalArgumentException("Invalid walk");
        moveX = directionX;
        moveZ = directionZ;
        moveSpeed = type.attributes().walkSpeed() * speedFactor;
    }

    public void stop() {
        moveSpeed = 0;
    }

    public boolean moving() {
        return moveSpeed > 0;
    }

    public void jump() {
        jumpRequested = true;
    }

    /** Turns the body toward a point; without a look target the body turns toward where it walks. */
    public void lookAt(double targetX, double targetZ) {
        lookX = targetX;
        lookZ = targetZ;
        looking = true;
    }

    public void stopLooking() {
        looking = false;
    }

    /** What the mob is visibly doing, for animation; progress is in [0, 1]. */
    public void act(MobAction action, float progress) {
        if (!(progress >= 0 && progress <= 1)) throw new IllegalArgumentException("Action progress must be in [0, 1]");
        this.action = Objects.requireNonNull(action);
        actionProgress = progress;
    }

    public MobAction action() {
        return action;
    }

    public float actionProgress() {
        return actionProgress;
    }

    void turn() {
        double dx, dz;
        if (looking) {
            dx = lookX - x;
            dz = lookZ - z;
        } else if (moving()) {
            dx = moveX;
            dz = moveZ;
        } else return;
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) return;
        // Yaw 0 faces -Z and positive yaw turns toward +X, matching the model and the hitbox look line.
        float target = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float delta = wrapDegrees(target - yaw);
        yaw = wrapDegrees(yaw + Math.clamp(delta, -MAX_TURN, MAX_TURN));
    }

    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
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

    /** Returns false when the hit was ignored by the hurt cooldown. */
    public boolean damage(DamageSource source, float amount) {
        return health.damage(source, amount).landed();
    }

    /** Pushes the mob in blocks per second, lifting it off the ground like Minecraft knockback. */
    public void knockback(double pushX, double pushY, double pushZ) {
        velocityX = velocityX * .5 + pushX;
        velocityY = Math.max(velocityY, pushY);
        velocityZ = velocityZ * .5 + pushZ;
        onGround = false;
    }

    void ageHurt() {
        health.tick();
    }

    @Override
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
                previousYaw,
                yaw,
                previousWalkDistance,
                walkDistance,
                burning(),
                health.hurtTicks() / (float) Health.HURT_TICKS,
                action,
                actionProgress);
    }
}
