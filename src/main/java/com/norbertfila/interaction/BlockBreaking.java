package com.norbertfila.interaction;

import com.norbertfila.items.Mining;
import com.norbertfila.items.Tool;
import com.norbertfila.items.ToolContext;
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
    private double fraction, creativeCooldown;

    public BlockBreaking(World world) {
        this.world = Objects.requireNonNull(world);
    }

    public boolean tick(Mode mode, Optional<VoxelRaycaster.Hit> target, boolean held, boolean pressed, double seconds) {
        return tick(mode, target, held, pressed, ToolContext.HAND, seconds);
    }

    public boolean tick(
            Mode mode,
            Optional<VoxelRaycaster.Hit> target,
            boolean held,
            boolean pressed,
            ToolContext tool,
            double seconds) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(tool);
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
            // Minecraft swords cannot break blocks in creative, so that fighting near terrain leaves it intact.
            if (tool.tool().filter(item -> item.type() == Tool.Type.SWORD).isPresent()) return false;
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
        // Progress accumulates per tick so that switching tools mid-break keeps the work already done.
        double duration = Mining.seconds((byte) blockId, tool);
        fraction = duration == 0 ? 1 : fraction + seconds / duration;
        if (fraction + TIME_EPSILON < 1) return false;
        resetProgress();
        return true;
    }

    public Optional<Progress> progress() {
        if (block == null) return Optional.empty();
        return Optional.of(new Progress(block, blockId, Math.min(1, fraction)));
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
        fraction = 0;
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
