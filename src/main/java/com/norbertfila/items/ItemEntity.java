package com.norbertfila.items;

import com.norbertfila.world.Aabb;

final class ItemEntity {
    static final double HALF_SIZE = .125;
    final long id;
    final int itemId;
    int count, pickupDelayTicks, ageTicks;
    double x, y, z, vx, vy, vz, previousX, previousY, previousZ;
    boolean onGround, moving;

    ItemEntity(ItemSnapshot state) {
        id = state.id();
        itemId = state.itemId();
        count = state.count();
        x = state.x();
        y = state.y();
        z = state.z();
        vx = state.vx();
        vy = state.vy();
        vz = state.vz();
        pickupDelayTicks = state.pickupDelayTicks();
        ageTicks = state.ageTicks();
        onGround = state.onGround();
        pause();
    }

    void pause() {
        previousX = x;
        previousY = y;
        previousZ = z;
        moving = false;
    }

    Aabb body() {
        return new Aabb(x - HALF_SIZE, y - HALF_SIZE, z - HALF_SIZE, x + HALF_SIZE, y + HALF_SIZE, z + HALF_SIZE);
    }

    ItemSnapshot snapshot() {
        return new ItemSnapshot(id, itemId, count, x, y, z, vx, vy, vz, pickupDelayTicks, ageTicks, onGround);
    }
}
