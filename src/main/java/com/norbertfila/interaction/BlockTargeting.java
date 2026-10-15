package com.norbertfila.interaction;

import com.norbertfila.player.PlayerController;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.World;
import java.util.Objects;
import java.util.Optional;

public final class BlockTargeting {
    private final World world;
    private final VoxelRaycaster raycaster = new VoxelRaycaster();

    public BlockTargeting(World world) {
        this.world = Objects.requireNonNull(world);
    }

    public Optional<VoxelRaycaster.Hit> find(
            PlayerController.Mode mode,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ) {
        Objects.requireNonNull(mode);
        if (mode == PlayerController.Mode.SPECTATOR) return Optional.empty();
        return raycaster.cast(
                world,
                originX,
                originY,
                originZ,
                directionX,
                directionY,
                directionZ,
                mode == PlayerController.Mode.SURVIVAL ? 4.5 : 6,
                id -> mode == PlayerController.Mode.CREATIVE
                        || BlockRegistry.DEFAULT.definition((byte) id).targetable());
    }
}
