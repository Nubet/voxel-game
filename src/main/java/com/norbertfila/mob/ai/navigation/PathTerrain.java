package com.norbertfila.mob.ai.navigation;

/** How pathfinding sees one block cell. */
public interface PathTerrain {
    enum Cell {
        /** Unloaded or otherwise unknown; never entered. */
        UNKNOWN,
        /** Collidable; can be stood on but not walked through. */
        SOLID,
        OPEN,
        WATER,
        /** Lava, fire and similar; never entered. */
        DANGER
    }

    Cell cell(int x, int y, int z);
}
