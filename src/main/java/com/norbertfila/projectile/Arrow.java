package com.norbertfila.projectile;

import com.norbertfila.combat.AttackTarget;

/** One arrow in flight or stuck in a block. */
final class Arrow {
    final long id;
    final AttackTarget shooter;
    double x, y, z, previousX, previousY, previousZ;
    double velocityX, velocityY, velocityZ;
    double directionX, directionY, directionZ;
    boolean stuck;
    int ageTicks;

    Arrow(long id, AttackTarget shooter, double x, double y, double z, double vx, double vy, double vz) {
        this.id = id;
        this.shooter = shooter;
        this.x = previousX = x;
        this.y = previousY = y;
        this.z = previousZ = z;
        velocityX = vx;
        velocityY = vy;
        velocityZ = vz;
        aim();
    }

    void aim() {
        double speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        if (speed < 1e-9) return;
        directionX = velocityX / speed;
        directionY = velocityY / speed;
        directionZ = velocityZ / speed;
    }

    ArrowRenderState renderState() {
        return new ArrowRenderState(id, previousX, previousY, previousZ, x, y, z, directionX, directionY, directionZ);
    }
}
