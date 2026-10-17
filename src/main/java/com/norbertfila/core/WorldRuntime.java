package com.norbertfila.core;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.commands.CommandSource;
import com.norbertfila.commands.GameCommands;
import com.norbertfila.diagnostics.SimulationMetrics;
import com.norbertfila.fluid.FluidScheduler;
import com.norbertfila.fluid.WaterSimulator;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.interaction.BlockBreakEvent;
import com.norbertfila.interaction.BlockBreaking;
import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.interaction.BlockTargeting;
import com.norbertfila.interaction.VoxelRaycaster;
import com.norbertfila.items.ItemRenderState;
import com.norbertfila.items.ItemSystem;
import com.norbertfila.items.ItemThrower;
import com.norbertfila.lod.FarTerrainStreamer;
import com.norbertfila.lod.FarTerrainTile;
import com.norbertfila.meshing.LayeredMesher;
import com.norbertfila.persistence.SaveScheduler;
import com.norbertfila.persistence.world.*;
import com.norbertfila.player.*;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

public final class WorldRuntime implements AutoCloseable {
    public enum State {
        LOADING,
        PLAYING,
        SAVING,
        SAVE_ERROR,
        STOPPED
    }

    private static final AtomicLong SESSION_IDS = new AtomicLong();
    private static final int CAPACITY = 64;
    private static final int FAR_TILE_SIZE = FarTerrainTile.SIZE;
    private static final int FAR_TERRAIN_WORKERS = 2;
    private static final double COLLISION_LOOKAHEAD_SECONDS = .5;
    private static final Logger LOG = Logger.getLogger(WorldRuntime.class.getName());
    private final ArrayBlockingQueue<Action> actions = new ArrayBlockingQueue<>(CAPACITY);
    private final ArrayBlockingQueue<GameCommands.Reply> commandReplies = new ArrayBlockingQueue<>(CAPACITY);
    private final ArrayBlockingQueue<Acknowledgement> acknowledgements = new ArrayBlockingQueue<>(CAPACITY);
    private final ArrayBlockingQueue<SessionMesh> meshes = new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicReference<Controls> controls =
            new AtomicReference<>(new Controls(PlayerInput.NONE, -90, -25, true, false, 4));
    private final AtomicReference<Frame> frame = new AtomicReference<>();
    private final Thread thread;
    private volatile boolean stopping;
    private volatile Throwable failure;
    private final long sessionId = SESSION_IDS.incrementAndGet();
    private final WorldIo io;
    private final SaveScheduler saves;
    private final boolean ownsIo;
    private final Controls initialControls;
    private volatile State state = State.LOADING;
    private volatile boolean activated, discard;
    private volatile WorldSnapshot finalSnapshot;
    private volatile ChunkPos initialView;
    private CompletableFuture<WorldSnapshot> startup;

    public WorldRuntime(TerrainSource generator, LayeredMesher mesher, ViewDemand demand, Path savePath) {
        this(generator, mesher, demand, savePath, ownedIo(generator, mesher, demand, savePath));
    }

    private static WorldIo ownedIo(TerrainSource generator, LayeredMesher mesher, ViewDemand demand, Path path) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(mesher);
        Objects.requireNonNull(demand);
        Objects.requireNonNull(path);
        return new WorldIo();
    }

    private WorldRuntime(TerrainSource generator, LayeredMesher mesher, ViewDemand demand, Path savePath, WorldIo io) {
        this(generator, mesher, demand, savePath, io, true, io.read(savePath));
    }

    public WorldRuntime(
            TerrainSource generator,
            LayeredMesher mesher,
            ViewDemand demand,
            Path savePath,
            WorldIo io,
            WorldSnapshot initial) {
        this(generator, mesher, demand, savePath, io, false, CompletableFuture.completedFuture(initial));
    }

    private WorldRuntime(
            TerrainSource generator,
            LayeredMesher mesher,
            ViewDemand demand,
            Path savePath,
            WorldIo io,
            boolean ownsIo,
            CompletableFuture<WorldSnapshot> initial) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(mesher);
        Objects.requireNonNull(demand);
        Objects.requireNonNull(savePath);
        this.io = io;
        this.ownsIo = ownsIo;
        saves = new SaveScheduler(io, savePath);
        ViewDemand sessionDemand =
                new ViewDemand(demand.horizontalRadius(), demand.minChunkY(), demand.maxChunkY(), demand.shape());
        initialControls = new Controls(PlayerInput.NONE, -90, -25, true, false, sessionDemand.horizontalRadius());
        controls.set(initialControls);
        startup = initial;
        thread = new Thread(() -> run(generator, mesher, sessionDemand), "voxel-world");
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

    public void acknowledge(SessionMesh mesh, boolean uploaded) {
        if (mesh.sessionId() != sessionId || stopping) return;
        if (!acknowledgements.offer(new Acknowledgement(mesh.mesh(), uploaded)))
            throw new IllegalStateException("Upload acknowledgement capacity exceeded");
    }

    public SessionMesh pollMesh() {
        return meshes.poll();
    }

    public Frame frame() {
        checkFailure();
        return frame.get();
    }

    private void checkFailure() {
        if (failure != null) throw new IllegalStateException("World runtime failed", failure);
    }

    private void run(TerrainSource generator, LayeredMesher mesher, ViewDemand demand) {
        try {
            var saved = startup.get();
            startup = null;
            var identity = saved.identity();
            if (identity.seed() != generator.seed() || identity.generatorVersion() != generator.generatorVersion())
                throw new IllegalStateException("Save does not match the world generator");
            if (stopping) return;
            World world = new World();
            WaterSimulator water =
                    new WaterSimulator(world, FAR_TILE_SIZE, FluidScheduler.DEFAULT_MAX_UPDATES_PER_TICK, true);
            long[] tick = {saved.fluids().tick()}, sequence = {saved.sequence()};
            WorldSnapshots.restore(world, saved, FAR_TILE_SIZE);
            water.restore(saved.fluids());
            PlayerController player = new PlayerController();
            Inventory inventory = new Inventory(saved.inventory());
            ItemSystem items = new ItemSystem(saved.items());
            ItemThrower thrower = new ItemThrower(world, inventory, items);
            if (saved.player().isPresent()) {
                var restored = saved.player().orElseThrow();
                player.restore(
                        new PlayerState.Position(restored.x(), restored.y(), restored.z()),
                        restored.mode(),
                        restored.flying());
                controls.compareAndSet(
                        initialControls,
                        new Controls(
                                PlayerInput.NONE,
                                restored.yaw(),
                                restored.pitch(),
                                true,
                                false,
                                demand.horizontalRadius()));
            } else player.setPosition(new SpawnResolver(generator).resolve(0, 0));
            var feet = player.state().position();
            var initialCenter = Coordinates.worldToChunk(
                    new BlockPos((int) Math.floor(feet.x()), (int) Math.floor(feet.y()), (int) Math.floor(feet.z())));
            initialView = new ChunkPos(
                    initialCenter.x(),
                    Math.clamp(initialCenter.y(), demand.minChunkY(), demand.maxChunkY()),
                    initialCenter.z());
            // Do not retain the loaded delta graph in the long-lived simulation closure.
            saved = null;
            PlayerInputBuffer input = new PlayerInputBuffer();
            Map<Long, ChunkManager.MeshResult> outstanding = new LinkedHashMap<>();
            Map<Long, Long> issuedAt = new HashMap<>();
            WaterSimulator.Metrics[] fluidMetrics = {water.metrics(tick[0])};
            long[] estimatedChunkBytes = {0};
            BlockBreaking breaking = new BlockBreaking(world);
            var commands = new GameCommands();
            CommandSource commandSource = new CommandSource() {
                @Override
                public boolean setMode(PlayerController.Mode mode) {
                    if (player.modeChangePending()) return false;
                    if (!player.setMode(world, mode)) return false;
                    inventory.returnCarried();
                    return true;
                }

                @Override
                public int give(byte block, int count) {
                    return inventory.insert(BlockId.toUnsignedInt(block), count);
                }
            };
            Deque<BlockBreakEvent> brokenBlocks = new ArrayDeque<>();
            BlockInteraction interaction = new BlockInteraction(world, FAR_TILE_SIZE, position -> {
                water.onBlockChanged(position, tick[0]);
                breaking.blockChanged(position);
            });
            BlockTargeting targeting = new BlockTargeting(world);
            try (ChunkManager chunks = new ChunkManager(world, generator, mesher, demand);
                    FarTerrainStreamer far = new FarTerrainStreamer(generator, world.changes(), FAR_TERRAIN_WORKERS)) {
                boolean[] viewReady = {false}, poseReady = {false};
                WorldEdit plantEdits = new WorldEdit(world, FAR_TILE_SIZE);
                chunks.setChunkLifecycleListener(position -> {
                    chunks.requestRemesh(plantEdits.validatePlants(position));
                    water.onChunkChanged(position, tick[0]);
                });
                FixedSimulation clock = new FixedSimulation(FixedSimulation.TICK_SECONDS, 5);
                HeldPlacementCooldown placementCooldown = new HeldPlacementCooldown();
                long[] lastPlacePress = {0};
                long previous = System.nanoTime();
                try {
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
                            if (activated && !state.paused()) tick[0] = Math.incrementExact(tick[0]);
                            if (activated && !state.paused()) placementCooldown.tick();
                            Acknowledgement ack;
                            while ((ack = acknowledgements.poll()) != null) {
                                if (outstanding.remove(ack.mesh().token()) == null) continue;
                                issuedAt.remove(ack.mesh().token());
                                if (!ack.uploaded() || !chunks.markUploaded(ack.mesh()))
                                    chunks.rejectUpload(ack.mesh());
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
                            // Before streaming, so that chunks loading outside the area this tick skip the water scan.
                            water.setSimulationArea(
                                    center, Math.min(state.simulationDistance(), state.renderDistance()), tick[0]);
                            long predictionStarted = System.nanoTime();
                            var collisionDemand = new LinkedHashSet<>(player.requiredCollisionChunks(
                                    world, input.peekTick(), dt, state.yaw(), state.pitch()));
                            collisionDemand.addAll(player.requiredCollisionChunks(
                                    world, input.peekTick(), COLLISION_LOOKAHEAD_SECONDS, state.yaw(), state.pitch()));
                            long predictionNanos = System.nanoTime() - predictionStarted;
                            long streamingStarted = System.nanoTime();
                            chunks.ensureCollisionChunks(List.copyOf(collisionDemand));
                            chunks.update(center);
                            var eye = player.renderEyePosition(1);
                            far.markEdited(world.changes().drainDirtyTiles());
                            far.update(eye.x(), eye.z(), state.renderDistance(), state.farDistance());
                            far.process();
                            // Upload backpressure must not prevent collision-data admission.
                            int meshBudget = Math.min(
                                    ChunkManager.RESULTS_PER_UPDATE,
                                    Math.min(CAPACITY - outstanding.size(), meshes.remainingCapacity()));
                            List<ChunkManager.MeshResult> ready = chunks.processResults(
                                            ChunkManager.RESULTS_PER_UPDATE, meshBudget)
                                    .meshes();
                            long streamingNanos = System.nanoTime() - streamingStarted;
                            if (!ready.isEmpty()) viewReady[0] = true;
                            if (!poseReady[0]
                                    && viewReady[0]
                                    && collisionDemand.stream().allMatch(chunks::readyForPlayer)) {
                                if (!player.fitRestoredPose(world))
                                    throw new IllegalStateException("Saved player position is obstructed");
                                poseReady[0] = true;
                            }
                            for (var mesh : ready) {
                                outstanding.put(mesh.token(), mesh);
                                issuedAt.put(mesh.token(), System.nanoTime());
                            }
                            Action action;
                            boolean interactionBlocked = false;
                            Edit breakPress = null;
                            for (int count = 0;
                                    count < CAPACITY
                                            && commandReplies.remainingCapacity() > 0
                                            && (action = actions.poll()) != null;
                                    count++) {
                                if (!activated && !(action instanceof ResetInput)) continue;
                                if ((state.blocked() || state.paused())
                                        && !(action instanceof ResetInput)
                                        && !(action instanceof InventoryAction)
                                        && !(action instanceof Command)
                                        && !(action instanceof SelectSlot)) continue;
                                switch (action) {
                                    case ChatCommand command -> {
                                        interactionBlocked = true;
                                        input.clear();
                                        player.clearInputGestures();
                                        breaking.cancel();
                                        commandReplies.add(commands.execute(command.text(), commandSource));
                                    }
                                    case ResetInput ignored -> {
                                        interactionBlocked = true;
                                        input.clear();
                                        player.clearInputGestures();
                                        breaking.cancel();
                                    }
                                    case Jump jump -> input.pressJump(jump.time());
                                    case NextMode ignored -> {
                                        interactionBlocked = true;
                                        inventory.returnCarried();
                                        player.requestNextMode();
                                        input.clear();
                                        breaking.cancel();
                                    }
                                    case Edit edit -> {
                                        if (edit.remove() && !player.modeChangePending()) {
                                            breakPress = edit;
                                        }
                                    }
                                    case SelectSlot selected -> {
                                        breaking.cancel();
                                        inventory.select(selected.slot());
                                    }
                                    case Teleport teleport -> {
                                        interactionBlocked = true;
                                        breaking.cancel();
                                        player.restore(
                                                new PlayerState.Position(teleport.x(), teleport.y(), teleport.z()),
                                                PlayerController.Mode.SPECTATOR,
                                                false);
                                    }
                                    case Fill fill ->
                                        chunks.requestRemesh(interaction.fill(fill.min(), fill.max(), fill.block()));
                                    case InventoryAction command -> {
                                        interactionBlocked = true;
                                        if (command instanceof ReturnCarried) inventory.returnCarried();
                                        else if (!player.modeChangePending()
                                                && player.mode() != PlayerController.Mode.SPECTATOR) {
                                            if (command instanceof ClickSlot click)
                                                inventory.click(click.slot(), click.right(), click.quickMove());
                                            else if (command instanceof PickCreative pick
                                                    && player.mode() == PlayerController.Mode.CREATIVE)
                                                inventory.creativePick(pick.slot(), pick.block());
                                            else if (command instanceof DropItem drop
                                                    && poseReady[0]
                                                    && !state.paused()
                                                    && (drop.fromInventory() || !state.blocked())) {
                                                int slot = drop.fromInventory()
                                                                && inventory
                                                                        .stack(Inventory.CARRIED)
                                                                        .isPresent()
                                                        ? Inventory.CARRIED
                                                        : drop.slot();
                                                thrower.drop(
                                                        slot,
                                                        drop.wholeStack(),
                                                        player.renderEyePosition(1),
                                                        drop.yaw(),
                                                        drop.pitch());
                                            }
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
                                if (!interactionBlocked && breakPress == null && !state.breakHeld())
                                    placeInventory(interaction, player, state, inventory)
                                            .ifPresent(chunks::requestRemesh);
                            }
                            // UI commands can arrive after this tick sampled gameplay controls.
                            if (!interactionBlocked
                                    && breakPress == null
                                    && !state.breakHeld()
                                    && !placementAttempted
                                    && placementCooldown.ready()
                                    && state.placeHeld()) {
                                placementCooldown.attempted();
                                placeInventory(interaction, player, state, inventory)
                                        .ifPresent(chunks::requestRemesh);
                            }
                            if (activated && !state.paused()) {
                                long waterStarted = System.nanoTime();
                                var waterResult = water.tick(tick[0]);
                                waterNanos = System.nanoTime() - waterStarted;
                                processed = waterResult.processedUpdates();
                                changed = waterResult.changedBlocks();
                                dirty = waterResult.dirtyChunks().size()
                                        + waterResult.levelDirtyChunks().size();
                                chunks.requestRemesh(waterResult.dirtyChunks());
                                chunks.requestWaterRemesh(waterResult.levelDirtyChunks());
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
                            } else player.pauseInterpolation();
                            if (activated
                                    && poseReady[0]
                                    && !state.blocked()
                                    && !state.paused()
                                    && !player.modeChangePending()
                                    && !interactionBlocked
                                    && (state.breakHeld() || breakPress != null)) {
                                Edit mining = breakPress == null
                                        ? new Edit(true, BlockId.AIR, state.yaw(), state.pitch())
                                        : breakPress;
                                var miningTarget = target(targeting, player, mining.yaw(), mining.pitch());
                                if (breaking.tick(
                                        player.mode(), miningTarget, state.breakHeld(), breakPress != null, dt)) {
                                    remove(interaction, player, mining, items)
                                            .filter(BlockChangeResult::changed)
                                            .ifPresent(result -> {
                                                chunks.requestRemesh(result);
                                                var hit = miningTarget.orElseThrow();
                                                brokenBlocks.addLast(new BlockBreakEvent(
                                                        tick[0],
                                                        hit.block(),
                                                        result.previous().orElseThrow()));
                                                if (brokenBlocks.size() > 32) brokenBlocks.removeFirst();
                                            });
                                }
                            } else breaking.cancel();
                            if (activated && !state.paused())
                                items.tick(
                                        world,
                                        center,
                                        Math.min(state.simulationDistance(), state.renderDistance()),
                                        dt,
                                        player.mode() == PlayerController.Mode.SURVIVAL && !player.modeChangePending()
                                                ? ItemSystem.pickupBox(
                                                        player.state().body())
                                                : null,
                                        inventory);
                            else items.pause();
                            if (activated && !state.paused() && tick[0] % 300 == 0) {
                                long captureStarted = System.nanoTime();
                                saves.request(capture(
                                        identity,
                                        ++sequence[0],
                                        world,
                                        water,
                                        tick[0],
                                        inventory,
                                        player,
                                        state,
                                        items));
                                captureNanos = System.nanoTime() - captureStarted;
                            }
                            saves.poll();
                            long publishStarted = System.nanoTime();
                            Map<Long, ChunkManager.MeshResult> valid = new HashMap<>();
                            outstanding.forEach((token, mesh) -> {
                                if (chunks.isCurrentMesh(mesh)) valid.put(token, mesh);
                            });
                            if (tick[0] % 60 == 0) {
                                fluidMetrics[0] = water.metrics(tick[0]);
                                estimatedChunkBytes[0] = world.estimatedBytes();
                            }
                            long contactStarted = System.nanoTime();
                            var contact = WaterContact.sample(world, player.state());
                            long contactNanos = System.nanoTime() - contactStarted;
                            var target = activated && poseReady[0] && !interactionBlocked
                                    ? target(targeting, player, state)
                                    : Optional.<VoxelRaycaster.Hit>empty();
                            long oldestAck =
                                    issuedAt.isEmpty() ? 0 : System.nanoTime() - Collections.min(issuedAt.values());
                            var residents = chunks.residency();
                            var farResidents = far.residency();
                            var coverage = chunks.coverage();
                            var validMeshes = Map.copyOf(valid);
                            var inventorySnapshot = inventory.snapshot();
                            var breaks = List.copyOf(brokenBlocks);
                            var itemSnapshot = items.renderSnapshot(world);
                            var biome = generator
                                    .sample(
                                            (int) Math.floor(
                                                    player.state().position().x()),
                                            (int) Math.floor(
                                                    player.state().position().z()))
                                    .biome();
                            long publishNanos = System.nanoTime() - publishStarted;
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
                                    estimatedChunkBytes[0],
                                    streamingNanos,
                                    publishNanos,
                                    far.metrics());
                            frame.set(new Frame(
                                    sessionId,
                                    poseReady[0],
                                    state.yaw(),
                                    state.pitch(),
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
                                    residents,
                                    farResidents,
                                    coverage,
                                    validMeshes,
                                    water.pendingUpdates(),
                                    metrics,
                                    inventorySnapshot,
                                    target,
                                    breaking.progress(),
                                    breaks,
                                    itemSnapshot,
                                    biome));
                            for (var mesh : ready) {
                                if (!meshes.offer(new SessionMesh(sessionId, mesh)))
                                    throw new IllegalStateException("Mesh publication capacity exceeded");
                            }
                        });
                        LockSupport.parkNanos(1_000_000);
                    }
                } finally {
                    if (activated)
                        finalSnapshot = capture(
                                identity,
                                ++sequence[0],
                                world,
                                water,
                                tick[0],
                                inventory,
                                player,
                                controls.get(),
                                items);
                }
            }
        } catch (Throwable problem) {
            failure = problem;
        } finally {
            startup = null;
            if (finalSnapshot != null) {
                this.state = State.SAVING;
                saves.request(finalSnapshot);
                while (!saves.isIdle() && !discard) {
                    this.state = saves.failure() == null ? State.SAVING : State.SAVE_ERROR;
                    LockSupport.parkNanos(10_000_000);
                }
            }
            if (ownsIo) io.close();
            state = State.STOPPED;
        }
    }

    private static WorldSnapshot capture(
            WorldIdentity identity,
            long sequence,
            World world,
            WaterSimulator water,
            long tick,
            Inventory inventory,
            PlayerController player,
            Controls controls,
            ItemSystem items) {
        var position = player.state().position();
        return WorldSnapshots.capture(
                identity,
                sequence,
                world,
                water.snapshot(tick),
                inventory.snapshot(),
                new SavedPlayerState(
                        position.x(),
                        position.y(),
                        position.z(),
                        (float) controls.yaw(),
                        (float) controls.pitch(),
                        player.mode(),
                        player.flying()),
                items.snapshot());
    }

    public long sessionId() {
        return sessionId;
    }

    public GameCommands.Reply pollCommandReply() {
        return commandReplies.poll();
    }

    /** The world-owner thread, for per-thread diagnostics such as allocation sampling. */
    public long threadId() {
        return thread.threadId();
    }

    public ChunkPos initialView() {
        return initialView;
    }

    public State state() {
        State current = state;
        return current == State.SAVE_ERROR && saves.failure() == null ? State.SAVING : current;
    }

    public Throwable saveFailure() {
        return saves.failure();
    }

    public WorldSnapshot retainedSnapshot() {
        return finalSnapshot;
    }

    public boolean activate() {
        var current = frame();
        if (stopping || current == null || !current.ready()) return false;
        activated = true;
        state = State.PLAYING;
        return true;
    }

    public void retrySave() {
        if (state() != State.SAVE_ERROR) throw new IllegalStateException("No failed final save");
        saves.retry();
        LockSupport.unpark(thread);
    }

    public void discardChanges() {
        if (state() != State.SAVE_ERROR) throw new IllegalStateException("Discard requires a failed final save");
        discard = true;
        LockSupport.unpark(thread);
    }

    public record SessionMesh(long sessionId, ChunkManager.MeshResult mesh) {
        public SessionMesh {
            if (sessionId < 1) throw new IllegalArgumentException("Invalid session ID");
            Objects.requireNonNull(mesh);
        }
    }

    private static Optional<VoxelRaycaster.Hit> target(
            BlockTargeting targeting, PlayerController player, Controls controls) {
        if (controls.blocked() || controls.paused() || player.modeChangePending()) return Optional.empty();
        return target(targeting, player, controls.yaw(), controls.pitch());
    }

    private static Optional<VoxelRaycaster.Hit> target(
            BlockTargeting targeting, PlayerController player, double yawDegrees, double pitchDegrees) {
        var eye = player.renderEyePosition(1);
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        return targeting.find(
                player.mode(),
                eye.x(),
                eye.y(),
                eye.z(),
                Math.cos(yaw) * Math.cos(pitch),
                Math.sin(pitch),
                Math.sin(yaw) * Math.cos(pitch));
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
            BlockInteraction interaction, PlayerController player, Edit edit, ItemSystem items) {
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
                items);
    }

    static Optional<BlockChangeResult> placeInventory(
            BlockInteraction interaction, PlayerController player, Controls controls, Inventory inventory) {
        byte block = inventory.selectedBlock();
        if (controls.blocked()
                || controls.paused()
                || controls.breakHeld()
                || block == BlockId.AIR
                || player.modeChangePending()) return Optional.empty();
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
        requestStop();
        boolean interrupted = false;
        while (thread.isAlive()) {
            if (state() == State.SAVE_ERROR) {
                if (interrupted) Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Final snapshot retained; retry or discard is required", saves.failure());
            }
            try {
                thread.join(10);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        checkFailure();
    }

    public void requestStop() {
        stopping = true;
        LockSupport.unpark(thread);
    }

    public boolean isStopped() {
        return !thread.isAlive();
    }

    public record Controls(
            PlayerInput input,
            double yaw,
            double pitch,
            boolean blocked,
            boolean paused,
            int renderDistance,
            long placePressId,
            boolean placeHeld,
            int farDistance,
            int simulationDistance,
            boolean breakHeld) {
        public Controls(
                PlayerInput input,
                double yaw,
                double pitch,
                boolean blocked,
                boolean paused,
                int renderDistance,
                long placePressId,
                boolean placeHeld,
                int farDistance,
                int simulationDistance) {
            this(
                    input,
                    yaw,
                    pitch,
                    blocked,
                    paused,
                    renderDistance,
                    placePressId,
                    placeHeld,
                    farDistance,
                    simulationDistance,
                    false);
        }

        public Controls(
                PlayerInput input, double yaw, double pitch, boolean blocked, boolean paused, int renderDistance) {
            this(input, yaw, pitch, blocked, paused, renderDistance, 0, false);
        }

        /** Controls without far terrain, simulating every loaded chunk. */
        public Controls(
                PlayerInput input,
                double yaw,
                double pitch,
                boolean blocked,
                boolean paused,
                int renderDistance,
                long placePressId,
                boolean placeHeld) {
            this(input, yaw, pitch, blocked, paused, renderDistance, placePressId, placeHeld, 0, renderDistance);
        }

        public Controls {
            Objects.requireNonNull(input);
            if (input.jumpPressed()
                    || !Double.isFinite(yaw)
                    || !Double.isFinite(pitch)
                    || !Float.isFinite((float) yaw)
                    || pitch < -90
                    || pitch > 90
                    || renderDistance < 0
                    || renderDistance > GameSettings.MAX_RENDER_DISTANCE
                    || farDistance < 0
                    || farDistance > GameSettings.MAX_FAR_DISTANCE
                    || simulationDistance < 0
                    || simulationDistance > GameSettings.MAX_RENDER_DISTANCE
                    || placePressId < 0) throw new IllegalArgumentException("Invalid runtime controls");
        }
    }

    public sealed interface Action permits Jump, NextMode, Edit, ResetInput, SelectSlot, InventoryAction, Command {}

    /** Scripted world commands; accepted even while gameplay input is blocked. */
    public sealed interface Command extends Action permits Teleport, Fill, ChatCommand {}

    public record ChatCommand(String text) implements Command {
        public ChatCommand {
            if (text == null
                    || !text.startsWith("/")
                    || text.codePointCount(0, text.length()) > ChatHistory.MAX_INPUT
                    || text.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid command text");
        }
    }

    /** Moves the player to a position in spectator mode. */
    public record Teleport(double x, double y, double z) implements Command {
        public Teleport {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Invalid teleport target");
        }
    }

    /** Sets every loaded block in an inclusive box. */
    public record Fill(BlockPos min, BlockPos max, byte block) implements Command {
        public static final long MAX_BLOCKS = 1 << 16;

        public Fill {
            Objects.requireNonNull(min);
            Objects.requireNonNull(max);
            long volume =
                    ((long) max.x() - min.x() + 1) * ((long) max.y() - min.y() + 1) * ((long) max.z() - min.z() + 1);
            if (!BlockId.isValid(block)
                    || max.x() < min.x()
                    || max.y() < min.y()
                    || max.z() < min.z()
                    || volume > MAX_BLOCKS) throw new IllegalArgumentException("Invalid fill");
        }
    }

    public record SelectSlot(int slot) implements Action {
        public SelectSlot {
            if (slot < 0 || slot >= 9) throw new IllegalArgumentException("Invalid hotbar slot");
        }
    }

    public sealed interface InventoryAction extends Action permits ClickSlot, PickCreative, ReturnCarried, DropItem {}

    public record DropItem(int slot, boolean wholeStack, boolean fromInventory, double yaw, double pitch)
            implements InventoryAction {
        public DropItem {
            if (slot < 0
                    || slot > Inventory.CARRIED
                    || (!fromInventory && slot >= Inventory.HOTBAR_SLOTS)
                    || !Double.isFinite(yaw)
                    || !Double.isFinite(pitch)
                    || pitch < -90
                    || pitch > 90) throw new IllegalArgumentException("Invalid drop command");
        }
    }

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
            long sessionId,
            boolean ready,
            double yaw,
            double pitch,
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
            ChunkManager.Residency residency,
            FarTerrainStreamer.Residency far,
            ChunkManager.Coverage coverage,
            Map<Long, ChunkManager.MeshResult> validUploads,
            int pendingWater,
            SimulationMetrics metrics,
            InventorySnapshot inventory,
            Optional<VoxelRaycaster.Hit> target,
            Optional<BlockBreaking.Progress> breaking,
            List<BlockBreakEvent> brokenBlocks,
            List<ItemRenderState> items,
            BiomeType biome) {
        public Frame {
            Objects.requireNonNull(residency);
            Objects.requireNonNull(far);
            Objects.requireNonNull(coverage);
            validUploads = Map.copyOf(validUploads);
            Objects.requireNonNull(inventory);
            Objects.requireNonNull(target);
            Objects.requireNonNull(breaking);
            brokenBlocks = List.copyOf(brokenBlocks);
            items = List.copyOf(items);
        }

        public Map<ChunkPos, Long> residents() {
            return residency.tokens();
        }

        public boolean isCurrent(ChunkManager.MeshResult mesh) {
            return mesh.equals(validUploads.get(mesh.token()));
        }

        public boolean isCurrent(SessionMesh mesh) {
            return mesh != null && mesh.sessionId() == sessionId && isCurrent(mesh.mesh());
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
