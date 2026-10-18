package com.norbertfila.player;

import com.norbertfila.world.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class TeleportController {
    private static final long TIMEOUT_NANOS = 15_000_000_000L;
    private PlayerState.Position target;
    private long started;

    public record Result(String text, boolean error) {}

    public boolean request(PlayerState.Position position, long now) {
        if (pending()
                || Math.abs(position.x()) > Integer.MAX_VALUE - 1024.0
                || Math.abs(position.z()) > Integer.MAX_VALUE - 1024.0
                || position.y() < WorldBounds.MIN_Y + 1
                || position.y() + PlayerState.STANDING_HEIGHT + 1 >= WorldBounds.MAX_Y_EXCLUSIVE) return false;
        target = position;
        started = now;
        return true;
    }

    public boolean pending() {
        return target != null;
    }

    public void cancel() {
        target = null;
    }

    public List<ChunkPos> requiredChunks() {
        return target == null
                ? List.of()
                : VoxelCollision.requiredChunks(
                        VoxelCollision.requiredRegion(PlayerState.at(target).body(), 0, 0, 0));
    }

    public Optional<Result> update(World world, PlayerController player, Predicate<ChunkPos> ready, long now) {
        if (!pending()) return Optional.empty();
        if (now - started >= TIMEOUT_NANOS) {
            cancel();
            return Optional.of(new Result("Teleport timed out while loading destination.", true));
        }
        if (!requiredChunks().stream().allMatch(ready)) return Optional.empty();
        var destination = target;
        cancel();
        var body = PlayerState.at(destination)
                .withCrouching(player.state().crouching())
                .body();
        if (player.mode() != PlayerController.Mode.SPECTATOR && !new VoxelCollision().spaceFree(world, body))
            return Optional.of(new Result("Teleport target is blocked.", true));
        player.setPosition(destination);
        return Optional.of(
                new Result("Teleported to " + destination.x() + " " + destination.y() + " " + destination.z(), false));
    }
}
