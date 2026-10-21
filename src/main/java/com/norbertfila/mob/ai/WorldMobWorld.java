package com.norbertfila.mob.ai;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.interaction.VoxelRaycaster;
import com.norbertfila.mob.ai.navigation.PathTerrain;
import com.norbertfila.mob.ai.navigation.WorldPathTerrain;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.World;
import java.util.List;
import java.util.Objects;

/** Mob AI's view of the live world for one tick. */
public final class WorldMobWorld implements MobWorld {
    private final World world;
    private final PathTerrain terrain;
    private final List<AttackTarget> targets;
    private final VoxelRaycaster raycaster = new VoxelRaycaster();

    public WorldMobWorld(World world, List<AttackTarget> targets) {
        this.world = Objects.requireNonNull(world);
        terrain = new WorldPathTerrain(world);
        this.targets = List.copyOf(targets);
    }

    @Override
    public PathTerrain terrain() {
        return terrain;
    }

    @Override
    public boolean canSee(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6) return true;
        // Like Minecraft, anything with collision blocks sight, including glass and leaves.
        return raycaster
                .cast(
                        world,
                        fromX,
                        fromY,
                        fromZ,
                        dx,
                        dy,
                        dz,
                        distance,
                        id -> BlockRegistry.DEFAULT.isCollidable((byte) id))
                .isEmpty();
    }

    @Override
    public List<AttackTarget> targets() {
        return targets;
    }
}
