package com.norbertfila.mob.spawn;

import com.norbertfila.light.LightLevels;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockPos;
import java.util.OptionalInt;

/** The world as natural spawning sees it. Every query answers empty or false where chunks are missing. */
public interface SpawnEnvironment {
    /** Unsigned block id. */
    OptionalInt block(BlockPos position);

    /** True when the body is loaded and touches no collidable block. */
    boolean spaceFree(Aabb body);

    /** Y of the highest block that blocks sky light in the column. */
    OptionalInt surfaceTop(int x, int z);

    /** True when the chunk column takes part in spawning, for example because it is inside simulation distance. */
    boolean columnActive(int chunkX, int chunkZ);

    LightLevels light();

    /** Levels subtracted from sky light by night and weather, see {@code SkyDarkness}. */
    int skyDarkening();

    WorldTime time();
}
