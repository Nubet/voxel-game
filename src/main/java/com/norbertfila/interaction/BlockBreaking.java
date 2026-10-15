package com.norbertfila.interaction;

import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.*;
import java.util.Objects;
import java.util.Optional;

public final class BlockBreaking {
    private static final double CREATIVE_REPEAT_SECONDS = .2;
    private static final double TIME_EPSILON = 1e-9;
    private final World world;
    private BlockPos block;
    private int blockId;
    private Chunk chunk;
    private Mode mode;
    private double elapsed, creativeCooldown;

    public BlockBreaking(World world) {
        this.world = Objects.requireNonNull(world);
    }

    public boolean tick(Mode mode, Optional<VoxelRaycaster.Hit> target, boolean held, boolean pressed, double seconds) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(target);
        if (!Double.isFinite(seconds) || seconds < 0)
            throw new IllegalArgumentException("Breaking delta must be finite and non-negative");
        if (this.mode != mode || pressed) cancel();
        this.mode = mode;
        if (mode == Mode.SPECTATOR || target.isEmpty() || (!held && !(pressed && mode == Mode.CREATIVE))) {
            cancel();
            return false;
        }
        var hit = target.orElseThrow();
        Chunk currentChunk = world.getChunk(Coordinates.worldToChunk(hit.block()));
        if (currentChunk == null || world.getBlock(hit.block()).orElse(-1) != hit.blockId()) {
            cancel();
            return false;
        }
        if (mode == Mode.CREATIVE) {
            creativeCooldown = Math.max(0, creativeCooldown - seconds);
            if (creativeCooldown > TIME_EPSILON) return false;
            creativeCooldown = CREATIVE_REPEAT_SECONDS;
            return true;
        }
        var definition = BlockRegistry.DEFAULT.definition((byte) hit.blockId());
        if (!definition.breakable()) {
            cancel();
            return false;
        }
        if (!hit.block().equals(block) || hit.blockId() != blockId || currentChunk != chunk) {
            resetProgress();
            block = hit.block();
            blockId = hit.blockId();
            chunk = currentChunk;
        }
        elapsed += seconds;
        if (elapsed + TIME_EPSILON < definition.breakSeconds()) return false;
        resetProgress();
        return true;
    }

    public Optional<Progress> progress() {
        if (block == null) return Optional.empty();
        double duration = BlockRegistry.DEFAULT.definition((byte) blockId).breakSeconds();
        return Optional.of(new Progress(block, blockId, Math.min(1, elapsed / duration)));
    }

    public void blockChanged(BlockPos position) {
        if (position.equals(block)) resetProgress();
    }

    public void cancel() {
        resetProgress();
        creativeCooldown = 0;
    }

    private void resetProgress() {
        block = null;
        chunk = null;
        elapsed = 0;
    }

    public record Progress(BlockPos block, int blockId, double fraction) {
        public Progress {
            Objects.requireNonNull(block);
            if (!BlockId.isValid((byte) blockId)
                    || blockId <= 0
                    || blockId > 255
                    || !Double.isFinite(fraction)
                    || fraction < 0
                    || fraction > 1) throw new IllegalArgumentException("Invalid breaking progress");
        }
    }
}
