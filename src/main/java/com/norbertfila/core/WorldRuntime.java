package com.norbertfila.core;

import com.norbertfila.diagnostics.SimulationMetrics;
import com.norbertfila.fluid.FluidScheduler;
import com.norbertfila.fluid.WaterSimulator;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.meshing.ChunkMesher;
import com.norbertfila.persistence.SaveScheduler;
import com.norbertfila.persistence.WorldSave;
import com.norbertfila.player.*;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

public final class WorldRuntime implements AutoCloseable {
    private static final int CAPACITY = 64;
    private static final int FAR_TILE_SIZE = 64;
    private static final double COLLISION_LOOKAHEAD_SECONDS = .5;
    private static final Logger LOG = Logger.getLogger(WorldRuntime.class.getName());
    private final ArrayBlockingQueue<Action> actions = new ArrayBlockingQueue<>(CAPACITY);
    private final ArrayBlockingQueue<Acknowledgement> acknowledgements = new ArrayBlockingQueue<>(CAPACITY);
    private final ArrayBlockingQueue<ChunkManager.MeshResult> meshes = new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicReference<Controls> controls =
            new AtomicReference<>(new Controls(PlayerInput.NONE, -90, -25, true, false, 4));
    private final AtomicReference<Frame> frame = new AtomicReference<>();
    private final Thread thread;
    private volatile boolean stopping;
    private volatile Throwable failure;

    public WorldRuntime(TerrainSource generator, ChunkMesher mesher, ViewDemand demand, Path savePath) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(mesher);
        Objects.requireNonNull(demand);
        Objects.requireNonNull(savePath);
        controls.set(new Controls(PlayerInput.NONE, -90, -25, true, false, demand.horizontalRadius()));
        thread = new Thread(() -> run(generator, mesher, demand, savePath), "voxel-world");
        thread.start();
    }

    public void controls(Controls value) {
        Objects.requireNonNull(value);
        Controls previous = controls.getAndSet(value);
        if ((value.blocked() || value.paused())
                && !previous.blocked()
                && !previous.paused()
                && !offer(new ResetInput())) throw new IllegalStateException("World command queue is full");
    }

    public boolean offer(Action action) {
        return !stopping && actions.offer(Objects.requireNonNull(action));
    }

    public void acknowledge(ChunkManager.MeshResult mesh, boolean uploaded) {
        if (!acknowledgements.offer(new Acknowledgement(mesh, uploaded)))
            throw new IllegalStateException("Upload acknowledgement capacity exceeded");
    }

    public ChunkManager.MeshResult pollMesh() {
        return meshes.poll();
    }

    public Frame frame() {
        checkFailure();
        return frame.get();
    }

    private void checkFailure() {
        if (failure != null) throw new IllegalStateException("World runtime failed", failure);
    }

    private void run(TerrainSource generator, ChunkMesher mesher, ViewDemand demand, Path savePath) {
        try {
            World world = new World();
            WaterSimulator water =
                    new WaterSimulator(world, FAR_TILE_SIZE, FluidScheduler.DEFAULT_MAX_UPDATES_PER_TICK, true);
            long[] tick = {0};
            InventorySnapshot restoredInventory = InventorySnapshot.EMPTY;
            if (Files.exists(savePath)) {
                var saved = WorldSave.load(savePath);
                if (saved.seed() != generator.seed() || saved.generatorVersion() != generator.generatorVersion())
                    throw new IllegalStateException("Save does not match the world generator");
                WorldSave.restore(world, saved, FAR_TILE_SIZE);
                water.restore(saved.fluids());
                tick[0] = saved.fluids().tick();
                restoredInventory = saved.inventory();
            }
            PlayerController player = new PlayerController();
            Inventory inventory = new Inventory(restoredInventory);
            player.setPosition(new SpawnResolver(generator).resolve(0, 0));
            PlayerInputBuffer input = new PlayerInputBuffer();
            Map<Long, ChunkManager.MeshResult> outstanding = new LinkedHashMap<>();
            Map<Long, Long> issuedAt = new HashMap<>();
            WaterSimulator.Metrics[] fluidMetrics = {water.metrics(tick[0])};
            long[] estimatedChunkBytes = {0};
            BlockInteraction interaction =
                    new BlockInteraction(world, FAR_TILE_SIZE, position -> water.onBlockChanged(position, tick[0]));
            try (ChunkManager chunks = new ChunkManager(world, generator, mesher, demand);
                    SaveScheduler saves = new SaveScheduler(savePath, 0)) {
                WorldEdit plantEdits = new WorldEdit(world, FAR_TILE_SIZE);
                chunks.setChunkLifecycleListener(position -> {
                    chunks.requestRemesh(plantEdits.validatePlants(position));
                    water.onChunkChanged(position, tick[0]);
                });
                FixedSimulation clock = new FixedSimulation(FixedSimulation.TICK_SECONDS, 5);
                HeldPlacementCooldown placementCooldown = new HeldPlacementCooldown();
                long[] lastPlacePress = {0};
                long previous = System.nanoTime();
                while (!stopping) {
                    long now = System.nanoTime();
                    double delta = Math.min(.1, (now - previous) / 1e9);
                    previous = now;
                    clock.advance(delta, dt -> {
                        long tickStarted = System.nanoTime();
                        long waterNanos = 0, playerNanos = 0, captureNanos = 0;
                        int processed = 0, changed = 0, dirty = 0;
                        boolean movementWaiting = false;
                        Controls state = controls.get();
                        if (!state.paused()) tick[0] = Math.incrementExact(tick[0]);
                        if (!state.paused()) placementCooldown.tick();
                        Acknowledgement ack;
                        while ((ack = acknowledgements.poll()) != null) {
                            if (outstanding.remove(ack.mesh().token()) == null) continue;
                            issuedAt.remove(ack.mesh().token());
                            if (!ack.uploaded() || !chunks.markUploaded(ack.mesh())) chunks.rejectUpload(ack.mesh());
                        }
                        chunks.setRenderDistance(state.renderDistance());
                        input.updateHeld(state.input());
                        if (state.blocked() || state.paused()) {
                            input.clear();
                            player.clearInputGestures();
                        }
                        var position = player.state().position();
                        ChunkPos center = Coordinates.worldToChunk(
                                new BlockPos((int) Math.floor(position.x()), (int) Math.floor(position.y()), (int)
                                        Math.floor(position.z())));
                        long predictionStarted = System.nanoTime();
                        var collisionDemand = new LinkedHashSet<>(player.requiredCollisionChunks(
                                world, input.peekTick(), dt, state.yaw(), state.pitch()));
                        collisionDemand.addAll(player.requiredCollisionChunks(
                                world, input.peekTick(), COLLISION_LOOKAHEAD_SECONDS, state.yaw(), state.pitch()));
                        long predictionNanos = System.nanoTime() - predictionStarted;
                        chunks.ensureCollisionChunks(List.copyOf(collisionDemand));
                        chunks.update(center);
                        // Upload backpressure must not prevent collision-data admission.
                        int meshBudget = Math.min(
                                ChunkManager.RESULTS_PER_UPDATE,
                                Math.min(CAPACITY - outstanding.size(), meshes.remainingCapacity()));
                        List<ChunkManager.MeshResult> ready =
                                chunks.processResults(4, meshBudget).meshes();
                        for (var mesh : ready) {
                            outstanding.put(mesh.token(), mesh);
                            issuedAt.put(mesh.token(), System.nanoTime());
                        }
                        Action action;
                        boolean inventoryInput = false;
                        for (int count = 0; count < CAPACITY && (action = actions.poll()) != null; count++) {
                            if ((state.blocked() || state.paused())
                                    && !(action instanceof ResetInput)
                                    && !(action instanceof InventoryAction)
                                    && !(action instanceof SelectSlot)) continue;
                            switch (action) {
                                case ResetInput ignored -> {
                                    input.clear();
                                    player.clearInputGestures();
                                }
                                case Jump jump -> input.pressJump(jump.time());
                                case NextMode ignored -> {
                                    inventory.returnCarried();
                                    player.requestNextMode();
                                    input.clear();
                                }
                                case Edit edit -> {
                                    if (edit.remove() && !player.modeChangePending()) {
                                        var result = remove(interaction, player, edit, inventory);
                                        result.ifPresent(chunks::requestRemesh);
                                    }
                                }
                                case SelectSlot selected -> inventory.select(selected.slot());
                                case InventoryAction command -> {
                                    inventoryInput = true;
                                    if (command instanceof ReturnCarried) inventory.returnCarried();
                                    else if (!player.modeChangePending()
                                            && player.mode() != PlayerController.Mode.SPECTATOR) {
                                        if (command instanceof ClickSlot click)
                                            inventory.click(click.slot(), click.right(), click.quickMove());
                                        else if (command instanceof PickCreative pick
                                                && player.mode() == PlayerController.Mode.CREATIVE)
                                            inventory.creativePick(pick.slot(), pick.block());
                                    }
                                }
                            }
                        }
                        boolean newPress = state.placePressId() != lastPlacePress[0];
                        lastPlacePress[0] = state.placePressId();
                        boolean placementAttempted = false;
                        if (newPress) {
                            placementAttempted = true;
                            placementCooldown.attempted();
                            if (!inventoryInput)
                                placeInventory(interaction, player, state, inventory)
                                        .ifPresent(chunks::requestRemesh);
                        }
                        // UI commands can arrive after this tick sampled gameplay controls.
                        if (!inventoryInput && !placementAttempted && placementCooldown.ready() && state.placeHeld()) {
                            placementCooldown.attempted();
                            placeInventory(interaction, player, state, inventory)
                                    .ifPresent(chunks::requestRemesh);
                        }
                        if (!state.paused()) {
                            long waterStarted = System.nanoTime();
                            var waterResult = water.tick(tick[0]);
                            waterNanos = System.nanoTime() - waterStarted;
                            processed = waterResult.processedUpdates();
                            changed = waterResult.changedBlocks();
                            dirty = waterResult.dirtyChunks().size();
                            chunks.requestRemesh(waterResult.dirtyChunks());
                            long playerStarted = System.nanoTime();
                            if (!state.blocked()
                                    && player
                                            .requiredCollisionChunks(
                                                    world, input.peekTick(), dt, state.yaw(), state.pitch())
                                            .stream()
                                            .allMatch(chunks::readyForPlayer)) {
                                var before = player.mode();
                                boolean wasFlying = player.flying(),
                                        wasCrouching = player.state().crouching();
                                player.tick(world, input.nextTick(), dt, state.yaw(), state.pitch());
                                if (before != player.mode()) LOG.info("Player mode changed to " + player.mode());
                                if (wasFlying != player.flying())
                                    LOG.info("Creative flight changed to " + player.flying());
                                if (wasCrouching != player.state().crouching())
                                    LOG.info("Player crouching changed to "
                                            + player.state().crouching());
                            } else {
                                movementWaiting = !state.blocked();
                                player.pauseInterpolation();
                            }
                            playerNanos = System.nanoTime() - playerStarted;
                            if (tick[0] % 300 == 0) {
                                long captureStarted = System.nanoTime();
                                saves.request(
                                        WorldSave.capture(
                                                generator.seed(),
                                                generator.generatorVersion(),
                                                world,
                                                water.snapshot(tick[0]),
                                                inventory.snapshot()),
                                        tick[0]);
                                captureNanos = System.nanoTime() - captureStarted;
                            }
                        } else player.pauseInterpolation();
                        if (saves.failure() != null)
                            throw new IllegalStateException("World save failed", saves.failure());
                        Map<Long, ChunkManager.MeshResult> valid = new HashMap<>();
                        outstanding.forEach((token, mesh) -> {
                            if (chunks.isCurrentMesh(mesh)) valid.put(token, mesh);
                        });
                        if (tick[0] % 60 == 0) {
                            fluidMetrics[0] = water.metrics(tick[0]);
                            estimatedChunkBytes[0] = world.residentPositions().stream()
                                    .mapToLong(p -> world.getChunk(p).estimatedBytes())
                                    .sum();
                        }
                        long contactStarted = System.nanoTime();
                        var contact = WaterContact.sample(world, player.state());
                        long contactNanos = System.nanoTime() - contactStarted;
                        long oldestAck =
                                issuedAt.isEmpty() ? 0 : System.nanoTime() - Collections.min(issuedAt.values());
                        var metrics = new SimulationMetrics(
                                System.nanoTime() - tickStarted,
                                predictionNanos,
                                waterNanos,
                                playerNanos,
                                contactNanos,
                                captureNanos,
                                processed,
                                changed,
                                dirty,
                                fluidMetrics[0],
                                chunks.metrics(),
                                saves.metrics(),
                                outstanding.size(),
                                meshes.size(),
                                actions.size(),
                                oldestAck,
                                movementWaiting,
                                estimatedChunkBytes[0]);
                        frame.set(new Frame(
                                tick[0],
                                System.nanoTime(),
                                player.renderEyePosition(0),
                                player.renderEyePosition(1),
                                player.mode(),
                                player.modeChangePending(),
                                player.state(),
                                contact,
                                player.flying(),
                                state.paused(),
                                chunks.residentTokens(),
                                Map.copyOf(valid),
                                water.pendingUpdates(),
                                metrics,
                                inventory.snapshot()));
                        for (var mesh : ready) {
                            if (!meshes.offer(mesh))
                                throw new IllegalStateException("Mesh publication capacity exceeded");
                        }
                    });
                    LockSupport.parkNanos(1_000_000);
                }
                saves.request(
                        WorldSave.capture(
                                generator.seed(),
                                generator.generatorVersion(),
                                world,
                                water.snapshot(tick[0]),
                                inventory.snapshot()),
                        tick[0]);
                saves.close();
                if (saves.failure() != null)
                    throw new IllegalStateException("Final world save failed", saves.failure());
            }
        } catch (Throwable problem) {
            failure = problem;
        }
    }

    static Optional<BlockChangeResult> edit(
            BlockInteraction interaction, PlayerController player, Edit edit, PlayerState.Position origin) {
        double yaw = Math.toRadians(edit.yaw()), pitch = Math.toRadians(edit.pitch());
        double dx = Math.cos(yaw) * Math.cos(pitch), dy = Math.sin(pitch), dz = Math.sin(yaw) * Math.cos(pitch);
        return edit.remove()
                ? interaction.remove(player.mode(), origin.x(), origin.y(), origin.z(), dx, dy, dz)
                : interaction.place(
                        origin.x(),
                        origin.y(),
                        origin.z(),
                        dx,
                        dy,
                        dz,
                        edit.block(),
                        player.mode(),
                        player.state().body());
    }

    static Optional<BlockChangeResult> remove(
            BlockInteraction interaction, PlayerController player, Edit edit, Inventory inventory) {
        double yaw = Math.toRadians(edit.yaw()), pitch = Math.toRadians(edit.pitch());
        var eye = player.renderEyePosition(1);
        return interaction.remove(
                player.mode(),
                eye.x(),
                eye.y(),
                eye.z(),
                Math.cos(yaw) * Math.cos(pitch),
                Math.sin(pitch),
                Math.sin(yaw) * Math.cos(pitch),
                player.mode() == PlayerController.Mode.SURVIVAL ? inventory : null);
    }

    static Optional<BlockChangeResult> placeInventory(
            BlockInteraction interaction, PlayerController player, Controls controls, Inventory inventory) {
        byte block = inventory.selectedBlock();
        if (controls.blocked() || controls.paused() || block == BlockId.AIR || player.modeChangePending())
            return Optional.empty();
        var result = edit(
                interaction,
                player,
                new Edit(false, block, controls.yaw(), controls.pitch()),
                player.renderEyePosition(1));
        if (player.mode() == PlayerController.Mode.SURVIVAL
                && result.filter(BlockChangeResult::changed).isPresent()) inventory.consumeSelected();
        return result;
    }

    @Override
    public void close() {
        stopping = true;
        LockSupport.unpark(thread);
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        checkFailure();
    }

    public record Controls(
            PlayerInput input,
            double yaw,
            double pitch,
            boolean blocked,
            boolean paused,
            int renderDistance,
            long placePressId,
            boolean placeHeld) {
        public Controls(
                PlayerInput input, double yaw, double pitch, boolean blocked, boolean paused, int renderDistance) {
            this(input, yaw, pitch, blocked, paused, renderDistance, 0, false);
        }

        public Controls {
            Objects.requireNonNull(input);
            if (input.jumpPressed()
                    || !Double.isFinite(yaw)
                    || !Double.isFinite(pitch)
                    || renderDistance < 0
                    || renderDistance > GameSettings.MAX_RENDER_DISTANCE
                    || placePressId < 0) throw new IllegalArgumentException("Invalid runtime controls");
        }
    }

    public sealed interface Action permits Jump, NextMode, Edit, ResetInput, SelectSlot, InventoryAction {}

    public record SelectSlot(int slot) implements Action {
        public SelectSlot {
            if (slot < 0 || slot >= 9) throw new IllegalArgumentException("Invalid hotbar slot");
        }
    }

    public sealed interface InventoryAction extends Action permits ClickSlot, PickCreative, ReturnCarried {}

    public record ClickSlot(int slot, boolean right, boolean quickMove) implements InventoryAction {
        public ClickSlot {
            if (slot < 0 || slot >= 36) throw new IllegalArgumentException("Invalid inventory slot");
        }
    }

    public record PickCreative(int slot, byte block) implements InventoryAction {
        public PickCreative {
            if (slot < 0 || slot >= 9 || block == BlockId.AIR || !BlockId.isValid(block))
                throw new IllegalArgumentException("Invalid creative selection");
        }
    }

    public record ReturnCarried() implements InventoryAction {}

    private record ResetInput() implements Action {}

    public record Jump(double time) implements Action {
        public Jump {
            if (!Double.isFinite(time) || time < 0) throw new IllegalArgumentException("Invalid jump timestamp");
        }
    }

    public record NextMode() implements Action {}

    public record Edit(boolean remove, byte block, double yaw, double pitch) implements Action {
        public Edit {
            if (!BlockId.isValid(block) || !Double.isFinite(yaw) || !Double.isFinite(pitch))
                throw new IllegalArgumentException("Invalid edit");
        }
    }

    private record Acknowledgement(ChunkManager.MeshResult mesh, boolean uploaded) {}

    public record Frame(
            long tick,
            long publishedAt,
            PlayerState.Position previousEye,
            PlayerState.Position eye,
            PlayerController.Mode mode,
            boolean modePending,
            PlayerState playerState,
            WaterContact.Result waterContact,
            boolean flying,
            boolean paused,
            Map<ChunkPos, Long> residents,
            Map<Long, ChunkManager.MeshResult> validUploads,
            int pendingWater,
            SimulationMetrics metrics,
            InventorySnapshot inventory) {
        public Frame {
            residents = Map.copyOf(residents);
            validUploads = Map.copyOf(validUploads);
            Objects.requireNonNull(inventory);
        }

        public boolean isCurrent(ChunkManager.MeshResult mesh) {
            return mesh.equals(validUploads.get(mesh.token()));
        }

        public PlayerState.Position interpolatedEye(long now) {
            double alpha = Math.clamp((now - publishedAt) / (1e9 * FixedSimulation.TICK_SECONDS), 0, 1);
            return new PlayerState.Position(
                    previousEye.x() + (eye.x() - previousEye.x()) * alpha,
                    previousEye.y() + (eye.y() - previousEye.y()) * alpha,
                    previousEye.z() + (eye.z() - previousEye.z()) * alpha);
        }
    }
}
