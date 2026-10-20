package com.norbertfila.mob.hazard;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockPos;

/** What environmental hazards need to know about the world around a mob. */
public interface HazardEnvironment {
    /** True while it is bright day; night, dusk and heavy storms count as dark. */
    boolean daylight();

    /** True when nothing above the position blocks sky light. */
    boolean seesSky(BlockPos position);

    /** True when rain, not snow, falls on the position from an open sky. */
    boolean rainingAt(BlockPos position);

    boolean touchesWater(Aabb body);
}
