package com.norbertfila.core;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.Health;
import com.norbertfila.combat.MeleeAttack;
import com.norbertfila.commands.CommandSource;
import com.norbertfila.commands.GameCommands;
import com.norbertfila.commands.LocationSearch;
import com.norbertfila.diagnostics.SimulationMetrics;
import com.norbertfila.fluid.FluidScheduler;
import com.norbertfila.fluid.FluidSimulator;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.interaction.BlockBreakEvent;
import com.norbertfila.interaction.BlockBreaking;
import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.interaction.BlockTargeting;
import com.norbertfila.interaction.EntityTargeting;
import com.norbertfila.interaction.VoxelRaycaster;
import com.norbertfila.items.ItemId;
import com.norbertfila.items.ItemRenderState;
import com.norbertfila.items.ItemSystem;
import com.norbertfila.items.ItemThrower;
import com.norbertfila.items.ToolContext;
import com.norbertfila.light.BlockLightState;
import com.norbertfila.light.BlockLighting;
import com.norbertfila.light.LightSample;
import com.norbertfila.lod.FarTerrainStreamer;
import com.norbertfila.lod.FarTerrainTile;
import com.norbertfila.meshing.LayeredMesher;
import com.norbertfila.mob.Mob;
import com.norbertfila.mob.MobCensus;
import com.norbertfila.mob.MobRenderState;
import com.norbertfila.mob.MobType;
import com.norbertfila.mob.PlayerPosition;
import com.norbertfila.mob.spawn.MobLifecycle;
import com.norbertfila.mob.spawn.NaturalSpawner;
import com.norbertfila.persistence.SaveScheduler;
import com.norbertfila.persistence.world.*;
import com.norbertfila.player.*;
import com.norbertfila.player.visual.PlayerAppearance;
import com.norbertfila.player.visual.PlayerRenderState;
import com.norbertfila.player.visual.PlayerRenderStateExtractor;
import com.norbertfila.projectile.ArrowRenderState;
import com.norbertfila.projectile.ArrowSystem;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.streaming.WorkerCounts;
import com.norbertfila.vegetation.PlantGrowthSystem;
import com.norbertfila.weather.BiomeWeather;
import com.norbertfila.weather.LocalWeather;
import com.norbertfila.weather.PrecipitationColumn;
import com.norbertfila.weather.PrecipitationField;
import com.norbertfila.weather.WeatherSample;
import com.norbertfila.weather.WeatherSystem;
import com.norbertfila.weather.WeatherType;
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
    private static final int MESH_CAPACITY = 128;
    private static final int FAR_TILE_SIZE = FarTerrainTile.SIZE;
    private static final double COLLISION_LOOKAHEAD_SECONDS = .5;
    private static final Logger LOG = Logger.getLogger(WorldRuntime.class.getName());
    private final ArrayBlockingQueue<Action> actions = new ArrayBlockingQueue<>(CAPACITY);
    private final ArrayBlockingQueue<GameCommands.Reply> commandReplies = new ArrayBlockingQueue<>(CAPACITY);
    private final ArrayBlockingQueue<Acknowledgement> acknowledgements = new ArrayBlockingQueue<>(MESH_CAPACITY);
    private final ArrayBlockingQueue<SessionMesh> meshes = new ArrayBlockingQueue<>(MESH_CAPACITY);
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
    private final WorkerCounts workers;

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
        this(generator, mesher, demand, savePath, io, true, io.read(savePath), WorkerCounts.DEFAULT);
    }

    public WorldRuntime(
            TerrainSource generator,
            LayeredMesher mesher,
            ViewDemand demand,
            Path savePath,
            WorldIo io,
            WorldSnapshot initial) {
        this(generator, mesher, demand, savePath, io, initial, WorkerCounts.DEFAULT);
    }

    public WorldRuntime(
            TerrainSource generator,
            LayeredMesher mesher,
            ViewDemand demand,
            Path savePath,
            WorldIo io,
            WorldSnapshot initial,
            WorkerCounts workers) {
        this(generator, mesher, demand, savePath, io, false, CompletableFuture.completedFuture(initial), workers);
    }

    private WorldRuntime(
            TerrainSource generator,
            LayeredMesher mesher,
            ViewDemand demand,
            Path savePath,
            WorldIo io,
            boolean ownsIo,
            CompletableFuture<WorldSnapshot> initial,
            WorkerCounts workers) {
        Objects.requireNonNull(generator);
        Objects.requireNonNull(mesher);
        Objects.requireNonNull(demand);
        Objects.requireNonNull(savePath);
        this.io = io;
        this.workers = Objects.requireNonNull(workers);
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
            FluidSimulator water =
                    new FluidSimulator(world, FAR_TILE_SIZE, FluidScheduler.DEFAULT_MAX_UPDATES_PER_TICK, true);
            long[] tick = {saved.fluids().tick()}, sequence = {saved.sequence()};
            WorldTime[] time = {saved.time()}, previousTime = {saved.time()};
            var weather = new WeatherSystem(identity.seed(), tick[0]);
            weather.restore(saved.weather(), tick[0]);
            WeatherSample[] previousWeather = {weather.sample(tick[0])};
            FixedSimulation clock = new FixedSimulation(FixedSimulation.TICK_SECONDS, 5);
            WorldSnapshots.restore(world, saved, FAR_TILE_SIZE);
            var growth = new PlantGrowthSystem(world, FAR_TILE_SIZE, identity.seed());
            growth.restore(saved.growth(), tick[0]);
            var biomeWeather = new BiomeWeather(generator);
            var precipitation = new PrecipitationField(world, biomeWeather);
            var explosionEdits = new WorldEdit(world, FAR_TILE_SIZE);
            List<ChunkPos> explosionDirty = new ArrayList<>();
            var arrows = new ArrowSystem();
            var mobLifecycle = new MobLifecycle(
                    world,
                    biomeWeather,
                    NaturalSpawner.overworld(),
                    identity.seed(),
                    destroyed -> {
                        for (var position : destroyed) {
                            var result = explosionEdits.setBlock(position, BlockId.AIR);
                            if (!result.changed()) continue;
                            explosionDirty.addAll(result.dirtyChunks());
                            water.onBlockChanged(position, tick[0]);
                        }
                    },
                    arrows);
            LightSample[] feetLight = {LightSample.UNKNOWN};
            var blockLighting = new BlockLighting(world);
            world.setBlockChangeListener(mutation -> {
                growth.onBlockChanged(mutation);
                precipitation.onBlockChanged(mutation);
                mobLifecycle.onBlockChanged(mutation);
                blockLighting.onBlockChanged(mutation);
            });
            water.restore(saved.fluids());
            PlayerController player = new PlayerController();
            var playerHealth = new Health(PlayerTarget.MAX_HEALTH);
            List<AttackTarget> attackTargets = List.of(new PlayerTarget(player, playerHealth));
            var playerVisuals = new PlayerRenderStateExtractor();
            Inventory inventory = new Inventory(saved.inventory());
            var combat = new PlayerCombat(inventory.selectedItem());
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
            FluidSimulator.Metrics[] fluidMetrics = {water.metrics(tick[0])};
            long[] estimatedChunkBytes = {0};
            BlockBreaking breaking = new BlockBreaking(world);
            var commands = new GameCommands();
            var teleports = new TeleportController();
            var searches = new LocationSearch(generator);
            CommandSource commandSource = new CommandSource() {
                @Override
                public long seed() {
                    return identity.seed();
                }

                @Override
                public WorldTime worldTime() {
                    return time[0];
                }

                @Override
                public void setTime(int minecraftTime) {
                    time[0] = time[0].withMinecraftTime(minecraftTime);
                    previousTime[0] = time[0];
                }

                @Override
                public double tickRate() {
                    return clock.tickRate();
                }

                @Override
                public void setTickRate(double rate) {
                    clock.setTickRate(rate);
                }

                @Override
                public WeatherType weather() {
                    return weather.snapshot().type();
                }

                @Override
                public void setWeather(WeatherType type) {
                    weather.set(type, tick[0]);
                    previousWeather[0] = weather.sample(tick[0]);
                }

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

                @Override
                public boolean teleport(PlayerState.Position position) {
                    return !player.modeChangePending() && teleports.request(position, System.nanoTime());
                }

                @Override
                public boolean locate(BiomeType biome) {
                    return searches.request(biome, player.state().position());
                }

                @Override
                public boolean locateRift() {
                    return searches.requestRift(player.state().position());
                }

                @Override
                public Optional<PlayerState.Position> playerPosition() {
                    return Optional.of(player.state().position());
                }

                @Override
                public boolean summon(MobType type, PlayerState.Position position) {
                    return mobLifecycle
                            .summon(type, position.x(), position.y(), position.z())
                            .isPresent();
                }

                @Override
                public MobCensus mobs() {
                    return mobLifecycle.census();
                }

                @Override
                public void setMobSpawning(boolean enabled) {
                    mobLifecycle.setNaturalSpawning(enabled);
                }

                @Override
                public int removeMobs() {
                    return mobLifecycle.removeAll();
                }
            };
            Deque<BlockBreakEvent> brokenBlocks = new ArrayDeque<>();
            BlockInteraction interaction = new BlockInteraction(world, FAR_TILE_SIZE, position -> {
                water.onBlockChanged(position, tick[0]);
                breaking.blockChanged(position);
            });
            BlockTargeting targeting = new BlockTargeting(world);
            try (searches;
                    ChunkManager chunks = new ChunkManager(
                            world,
                            generator,
                            mesher,
                            demand,
                            demand.horizontalRadius() + ChunkManager.UNLOAD_MARGIN,
                            workers);
                    FarTerrainStreamer far = new FarTerrainStreamer(generator, world.changes(), workers.far())) {
                boolean[] viewReady = {false}, poseReady = {false};
                WorldEdit plantEdits = new WorldEdit(world, FAR_TILE_SIZE);
                chunks.setChunkLifecycleListener(position -> {
                    chunks.requestRemesh(plantEdits.validatePlants(position));
                    water.onChunkChanged(position, tick[0]);
                    growth.onChunkChanged(position);
                    precipitation.onChunkChanged(position);
                    mobLifecycle.onChunkChanged(position);
                    blockLighting.onChunkChanged(position);
                });
                HeldPlacementCooldown placementCooldown = new HeldPlacementCooldown();
                long[] lastPlacePress = {0};
                long previous = System.nanoTime();
                try {
                    while (!stopping) {
                        long now = System.nanoTime();
                        boolean stalled = now - previous > 100_000_000L;
                        double delta = Math.min(.1, (now - previous) / 1e9);
                        previous = now;
                        clock.advance(delta, dt -> {
                            long tickStarted = System.nanoTime();
                            long waterNanos = 0, playerNanos = 0, captureNanos = 0;
                            int processed = 0, changed = 0, dirty = 0;
                            boolean movementWaiting = false;
                            Controls state = controls.get();
                            previousTime[0] = time[0];
                            previousWeather[0] = weather.sample(tick[0]);
                            if (activated && !state.paused()) time[0] = time[0].tick();
                            if (activated && !state.paused()) tick[0] = Math.incrementExact(tick[0]);
                            growth.advanceClock(tick[0]);
                            weather.update(tick[0]);
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
                            collisionDemand.addAll(teleports.requiredChunks());
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
                                    Math.min(MESH_CAPACITY - outstanding.size(), meshes.remainingCapacity()));
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
                                        teleports.cancel();
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
                            if (teleports.pending() && commandReplies.remainingCapacity() > 0) {
                                interactionBlocked = true;
                                input.clear();
                                breaking.cancel();
                                teleports
                                        .update(world, player, chunks::readyForPlayer, System.nanoTime())
                                        .ifPresent(result -> commandReplies.add(
                                                new GameCommands.Reply(result.text(), result.error())));
                            }
                            if (commandReplies.remainingCapacity() > 0)
                                searches.poll().ifPresent(commandReplies::add);
                            boolean newPress = state.placePressId() != lastPlacePress[0];
                            lastPlacePress[0] = state.placePressId();
                            boolean placementAttempted = false;
                            if (newPress) {
                                placementAttempted = true;
                                placementCooldown.attempted();
                                if (!interactionBlocked && breakPress == null && !state.breakHeld())
                                    placeInventory(interaction, player, state, inventory)
                                            .ifPresent(result -> {
                                                chunks.requestRemesh(result);
                                                if (result.changed()) playerVisuals.swing(tick[0], true);
                                            });
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
                                        .ifPresent(result -> {
                                            chunks.requestRemesh(result);
                                            if (result.changed()) playerVisuals.swing(tick[0], true);
                                        });
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
                                        && !interactionBlocked
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
                            if (stalled) player.pauseInterpolation();
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
                                var struck =
                                        targetMob(mobLifecycle, player, mining.yaw(), mining.pitch(), miningTarget);
                                if (struck.isPresent()) {
                                    breaking.cancel();
                                    if (breakPress != null) {
                                        boolean inWater = WaterContact.sample(world, player.state())
                                                        .submergedFraction()
                                                > 0;
                                        combat.attack(
                                                        struck.get().target(),
                                                        PlayerCombat.attacker(player, mining.yaw(), inWater),
                                                        mobLifecycle::near)
                                                .filter(MeleeAttack.Outcome::sprintHit)
                                                .ifPresent(hit -> player.endSprintByAttack());
                                        playerVisuals.swing(tick[0], true);
                                    }
                                } else {
                                    if (breakPress != null && miningTarget.isEmpty()) combat.miss();
                                    var tool = new ToolContext(inventory.selectedItem());
                                    boolean completed = breaking.tick(
                                            player.mode(),
                                            miningTarget,
                                            state.breakHeld(),
                                            breakPress != null,
                                            tool,
                                            dt);
                                    if (player.mode() != PlayerController.Mode.SPECTATOR
                                            && (breakPress != null
                                                    || completed
                                                    || breaking.progress().isPresent()
                                                    || (player.mode() == PlayerController.Mode.CREATIVE
                                                            && miningTarget.isPresent())))
                                        playerVisuals.swing(tick[0], breakPress != null);
                                    if (completed) {
                                        remove(interaction, player, mining, items, tool)
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
                                }
                            } else breaking.cancel();
                            var growthResult = growth.update(tick[0], activated && !state.paused());
                            chunks.requestRemesh(growthResult.dirtyChunks());
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
                            if (activated && !state.paused()) {
                                playerHealth.tick();
                                combat.tick(dt, inventory.selectedItem());
                                if (playerHealth.dead()) {
                                    playerHealth.restore();
                                    teleports.cancel();
                                    teleports.request(new SpawnResolver(generator).resolve(0, 0), System.nanoTime());
                                    commandReplies.offer(new GameCommands.Reply("You died!", true));
                                }
                            }
                            long mobStarted = System.nanoTime();
                            var standing = player.state().position();
                            if (activated && !state.paused()) {
                                var players = player.mode() == PlayerController.Mode.SPECTATOR
                                        ? List.<PlayerPosition>of()
                                        : List.of(new PlayerPosition(standing.x(), standing.y(), standing.z()));
                                mobLifecycle.tick(
                                        tick[0],
                                        dt,
                                        players,
                                        attackTargets,
                                        time[0],
                                        weather.sample(tick[0]),
                                        center,
                                        Math.min(state.simulationDistance(), state.renderDistance()));
                                if (!explosionDirty.isEmpty()) {
                                    chunks.requestRemesh(List.copyOf(explosionDirty));
                                    explosionDirty.clear();
                                }
                            }
                            if (tick[0] % 15 == 0)
                                feetLight[0] = LightSample.at(
                                        mobLifecycle.light(),
                                        new BlockPos(
                                                (int) Math.floor(standing.x()), (int) Math.floor(standing.y()), (int)
                                                        Math.floor(standing.z())));
                            long mobNanos = System.nanoTime() - mobStarted;
                            long lightStarted = System.nanoTime();
                            var lightEye = player.renderEyePosition(1);
                            var blockLight = blockLighting.update(
                                    new BlockPos((int) Math.floor(standing.x()), (int) Math.floor(standing.y()), (int)
                                            Math.floor(standing.z())),
                                    new BlockPos((int) Math.floor(lightEye.x()), (int) Math.floor(lightEye.y()), (int)
                                            Math.floor(lightEye.z())),
                                    player.mode() == PlayerController.Mode.SPECTATOR
                                            ? 0
                                            : BlockRegistry.DEFAULT.lightEmission(inventory.selectedBlock()));
                            long lightNanos = System.nanoTime() - lightStarted;
                            if (activated && !state.paused() && tick[0] % 300 == 0) {
                                long captureStarted = System.nanoTime();
                                saves.request(capture(
                                        identity,
                                        ++sequence[0],
                                        world,
                                        water,
                                        growth,
                                        weather,
                                        tick[0],
                                        inventory,
                                        player,
                                        state,
                                        items,
                                        time[0]));
                                captureNanos = System.nanoTime() - captureStarted;
                            }
                            saves.poll();
                            long publishStarted = System.nanoTime();
                            Map<Long, ChunkManager.MeshResult> valid = new HashMap<>();
                            long outstandingBytes = 0;
                            for (var entry : outstanding.entrySet()) {
                                var mesh = entry.getValue();
                                outstandingBytes += mesh.bytes();
                                if (chunks.isCurrentMesh(mesh)) valid.put(entry.getKey(), mesh);
                            }
                            if (tick[0] % 60 == 0) {
                                fluidMetrics[0] = water.metrics(tick[0]);
                                estimatedChunkBytes[0] = world.estimatedBytes();
                            }
                            long contactStarted = System.nanoTime();
                            var contact = WaterContact.sample(world, player.state());
                            var playerRenderState = playerVisuals.extract(
                                    player,
                                    state.yaw(),
                                    state.pitch(),
                                    contact,
                                    !activated || state.blocked() || state.paused() || movementWaiting || stalled,
                                    state.appearance(),
                                    tick[0]);
                            long contactNanos = System.nanoTime() - contactStarted;
                            var blockTarget = activated && poseReady[0] && !interactionBlocked
                                    ? target(targeting, player, state)
                                    : Optional.<VoxelRaycaster.Hit>empty();
                            var mobTarget = activated && poseReady[0] && !interactionBlocked
                                    ? targetMob(mobLifecycle, player, state, blockTarget)
                                    : Optional.<EntityTargeting.Hit<Mob>>empty();
                            var target = mobTarget.isPresent() ? Optional.<VoxelRaycaster.Hit>empty() : blockTarget;
                            long oldestAck =
                                    issuedAt.isEmpty() ? 0 : System.nanoTime() - Collections.min(issuedAt.values());
                            var residents = chunks.residency();
                            var farResidents = far.residency();
                            var coverage = chunks.coverage();
                            var validMeshes = Map.copyOf(valid);
                            var inventorySnapshot = inventory.snapshot();
                            var breaks = List.copyOf(brokenBlocks);
                            var itemSnapshot = items.renderSnapshot(world);
                            var cameraEnvironment = cameraEnvironment(world, player.renderEyePosition(1));
                            var weatherSample = weather.sample(tick[0]);
                            var weatherEye = player.renderEyePosition(1);
                            precipitation.update(weatherEye.x(), weatherEye.z(), weatherSample.precipitation() > 0);
                            var localWeather = biomeWeather.sample(weatherEye.x(), weatherEye.z());
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
                                    far.metrics(),
                                    outstandingBytes,
                                    mobNanos,
                                    mobLifecycle.size(),
                                    lightNanos);
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
                                    mobTarget.isPresent(),
                                    breaking.progress(),
                                    breaks,
                                    itemSnapshot,
                                    biome,
                                    playerRenderState,
                                    cameraEnvironment,
                                    previousTime[0],
                                    time[0],
                                    previousWeather[0],
                                    weatherSample,
                                    localWeather,
                                    precipitation.snapshot(),
                                    clock.tickRate(),
                                    mobLifecycle.renderSnapshot(),
                                    mobLifecycle.census(),
                                    feetLight[0],
                                    blockLight,
                                    playerHealth.value(),
                                    playerHealth.hurtTicks() / (float) Health.HURT_TICKS,
                                    arrows.renderSnapshot()));
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
                                growth,
                                weather,
                                tick[0],
                                inventory,
                                player,
                                controls.get(),
                                items,
                                time[0]);
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
            FluidSimulator water,
            PlantGrowthSystem growth,
            WeatherSystem weather,
            long tick,
            Inventory inventory,
            PlayerController player,
            Controls controls,
            ItemSystem items,
            WorldTime time) {
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
                items.snapshot(),
                time,
                growth.snapshot(),
                weather.snapshot());
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

    private static Optional<EntityTargeting.Hit<Mob>> targetMob(
            MobLifecycle mobs, PlayerController player, Controls controls, Optional<VoxelRaycaster.Hit> block) {
        if (controls.blocked() || controls.paused() || player.modeChangePending()) return Optional.empty();
        return targetMob(mobs, player, controls.yaw(), controls.pitch(), block);
    }

    /** A mob counts only when it is nearer than the targeted block, so walls hide mobs behind them. */
    private static Optional<EntityTargeting.Hit<Mob>> targetMob(
            MobLifecycle mobs,
            PlayerController player,
            double yawDegrees,
            double pitchDegrees,
            Optional<VoxelRaycaster.Hit> block) {
        double reach = block.map(hit -> Math.min(hit.distance(), EntityTargeting.reach(player.mode())))
                .orElse(EntityTargeting.reach(player.mode()));
        if (reach == 0) return Optional.empty();
        var eye = player.renderEyePosition(1);
        var ray = EntityTargeting.Ray.look(eye.x(), eye.y(), eye.z(), yawDegrees, pitchDegrees);
        return EntityTargeting.find(mobs.near(EntityTargeting.sweep(ray, reach)), ray, reach);
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
            BlockInteraction interaction, PlayerController player, Edit edit, ItemSystem items, ToolContext tool) {
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
                items,
                tool);
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
            boolean breakHeld,
            PlayerAppearance appearance) {
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
                int simulationDistance,
                boolean breakHeld) {
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
                    breakHeld,
                    PlayerAppearance.DEFAULT);
        }

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
            Objects.requireNonNull(appearance);
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
            if (slot < 0 || slot >= 9 || block == BlockId.AIR || !ItemId.isValid(block))
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

    private static CameraEnvironment cameraEnvironment(World world, PlayerState.Position eye) {
        var center = new ChunkPos((int) Math.floor(eye.x() / Chunk.SIZE), (int) Math.floor(eye.y() / Chunk.SIZE), (int)
                Math.floor(eye.z() / Chunk.SIZE));
        return new CameraEnvironment(center, world.hasChunk(center) ? world.blockNeighborhood(center) : null);
    }

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
            boolean entityTargeted,
            Optional<BlockBreaking.Progress> breaking,
            List<BlockBreakEvent> brokenBlocks,
            List<ItemRenderState> items,
            BiomeType biome,
            PlayerRenderState playerRenderState,
            CameraEnvironment cameraEnvironment,
            WorldTime previousTime,
            WorldTime worldTime,
            WeatherSample previousWeather,
            WeatherSample weather,
            LocalWeather localWeather,
            List<PrecipitationColumn> precipitation,
            double tickRate,
            List<MobRenderState> mobs,
            MobCensus mobCensus,
            LightSample feetLight,
            BlockLightState blockLight,
            float health,
            float hurt,
            List<ArrowRenderState> arrows) {
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
            Objects.requireNonNull(playerRenderState);
            Objects.requireNonNull(cameraEnvironment);
            Objects.requireNonNull(previousTime);
            Objects.requireNonNull(worldTime);
            Objects.requireNonNull(previousWeather);
            Objects.requireNonNull(weather);
            Objects.requireNonNull(localWeather);
            precipitation = List.copyOf(precipitation);
            mobs = List.copyOf(mobs);
            Objects.requireNonNull(mobCensus);
            Objects.requireNonNull(feetLight);
            Objects.requireNonNull(blockLight);
            arrows = List.copyOf(arrows);
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
            return playerRenderState.interpolate(interpolationAlpha(now)).eye();
        }

        public double interpolationAlpha(long now) {
            return Math.clamp((now - publishedAt) * 1e-9 * tickRate, 0, 1);
        }
    }
}
