package com.norbertfila.projectile;

/** Position and flight direction of one arrow for drawing; a stuck arrow keeps the direction it hit with. */
public record ArrowRenderState(
        long id,
        double previousX,
        double previousY,
        double previousZ,
        double x,
        double y,
        double z,
        double directionX,
        double directionY,
        double directionZ) {}
