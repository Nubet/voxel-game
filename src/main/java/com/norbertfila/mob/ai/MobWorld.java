package com.norbertfila.mob.ai;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.mob.ai.navigation.PathTerrain;
import java.util.List;

/** The world as mob AI sees and affects it; one implementation wraps the live world, tests use fakes. */
public interface MobWorld {
    PathTerrain terrain();

    /** True when no collidable block lies on the straight line between the two points. */
    boolean canSee(double fromX, double fromY, double fromZ, double toX, double toY, double toZ);

    /** Everything mobs may attack, attackable or not; goals filter with {@link AttackTarget#attackable()}. */
    List<AttackTarget> targets();

    /** Blows up at the point with Minecraft explosion power, hurting players and mobs and breaking blocks. */
    void explode(double x, double y, double z, float power);
}
