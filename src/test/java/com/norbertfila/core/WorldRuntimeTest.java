package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.items.ItemSnapshot;
import com.norbertfila.items.ItemState;
import com.norbertfila.items.ItemSystem;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.TestWorlds;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.player.PlayerState;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldRuntimeTest {
    @Test
    void publishesImmutableVisualsAndResetsBlockedPausedAndTeleportedHistory() throws Exception {
        try (var runtime = start(new Flat(), directory.resolve("player-visuals.vxl"))) {
            runtime.controls(controls(false, false));
            var playing = await(runtime, f -> f.tick() >= 3);
            assertEquals(
                    playing.playerState().position(),
                    playing.playerRenderState().current().feet());
            long interpolationTime =
                    playing.publishedAt() + (long) (1e9 * com.norbertfila.simulation.FixedSimulation.TICK_SECONDS * .5);
            assertEquals(.5, playing.interpolationAlpha(interpolationTime), 1e-7);
            assertEquals(
                    playing.playerRenderState()
                            .interpolate(playing.interpolationAlpha(interpolationTime))
                            .eye(),
                    playing.interpolatedEye(interpolationTime));
            var camera = new Camera();
            var eye = playing.interpolatedEye(interpolationTime);
            camera.setPosition(eye.x(), eye.y(), eye.z());
            assertFalse(GameLoop.localPlayerVisible(playing, interpolationTime, camera));
            camera.setPosition(eye.x() - 5, eye.y(), eye.z());
            assertTrue(GameLoop.localPlayerVisible(playing, interpolationTime, camera));

            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 179, 20, true, false, 0));
            var blocked = await(runtime, f -> f.playerRenderState().current().yaw() == 179);
            assertTrue(blocked.playerRenderState().frozen());
            assertEquals(
                    blocked.playerRenderState().current(),
                    blocked.playerRenderState().previous());
            assertTrue(runtime.offer(new WorldRuntime.Teleport(-500.25, 20, -800.75)));
            var teleported = await(runtime, f -> f.playerState().position().x() == -500.25);
            assertEquals(
                    teleported.playerRenderState().current(),
                    teleported.playerRenderState().previous());
            assertEquals(
                    playing.playerState().position(),
                    playing.playerRenderState().current().feet());

            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, -179, -20, true, true, 0));
            var paused = await(runtime, WorldRuntime.Frame::paused);
            assertEquals(-179, paused.playerRenderState().interpolate(.5).yaw());
            assertEquals(
                    paused.playerRenderState().current(),
                    paused.playerRenderState().previous());
            assertEquals(paused.eye(), paused.interpolatedEye(paused.publishedAt()));
        }
    }

    @Test
    void chatCommandsExecuteOnceWhileInputIsBlockedAndPersistOnlyGameState() throws Exception {
        Path save = directory.resolve("chat-commands.vxl");
        try (var runtime = start(new Flat(), save)) {
            await(runtime, f -> f.tick() >= 2);
            runtime.controls(controls(true, false));
            await(runtime, f -> f.target().isEmpty());
            assertTrue(runtime.offer(new WorldRuntime.ChatCommand("/seed")));
            assertTrue(runtime.offer(new WorldRuntime.ChatCommand("/give @s stone 64")));
            assertTrue(runtime.offer(new WorldRuntime.ChatCommand("/gamemode creative")));
            var executed = await(
                    runtime,
                    f -> f.mode() == PlayerController.Mode.CREATIVE
                            && f.inventory().count(0) == 64);
            var seedReply = runtime.pollCommandReply();
            assertFalse(seedReply.error());
            assertEquals("Seed: 42", seedReply.text());
            assertFalse(runtime.pollCommandReply().error());
            assertFalse(runtime.pollCommandReply().error());
            await(runtime, f -> f.tick() > executed.tick() + 2);
            assertNull(runtime.pollCommandReply());
            assertEquals(64, runtime.frame().inventory().count(0));
        }
        try (var runtime = start(new Flat(), save)) {
            var restored = await(runtime, f -> f.tick() >= 2);
            assertEquals(PlayerController.Mode.CREATIVE, restored.mode());
            assertEquals(64, restored.inventory().count(0));
            assertNull(runtime.pollCommandReply());
        }
    }

    @Test
    void thrownStacksAndTheirRemainingPickupDelaySurviveRestartWithoutDuplication() throws Exception {
        Path save = directory.resolve("throw-restart.vxl");
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 5);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        ItemSnapshot pausedItem;
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(controls(false, false));
            await(runtime, f -> f.tick() >= 2);
            assertTrue(runtime.offer(new WorldRuntime.DropItem(0, true, false, 0, 30)));
            await(runtime, f -> f.inventory().count(0) == 0 && !f.items().isEmpty());
            runtime.controls(controls(true, true));
            var paused = await(runtime, WorldRuntime.Frame::paused);
            pausedItem = paused.items().getFirst().item();
            assertEquals(5, pausedItem.count());
            assertTrue(pausedItem.pickupDelayTicks() > 0 && pausedItem.pickupDelayTicks() < 40);
        }
        var saved = TestWorlds.load(save);
        assertEquals(0, saved.inventory().count(0));
        assertEquals(java.util.List.of(pausedItem), saved.items().entities());
        try (var runtime = start(new Flat(), save)) {
            var restarted = await(
                    runtime,
                    f -> !f.items().isEmpty() && f.items().getFirst().item().ageTicks() > pausedItem.ageTicks());
            var item = restarted.items().getFirst().item();
            assertEquals(pausedItem.id(), item.id());
            assertEquals(5, item.count());
            assertTrue(item.pickupDelayTicks() < pausedItem.pickupDelayTicks());
            assertTrue(item.x() > pausedItem.x());
            assertEquals(0, restarted.inventory().count(0));
        }
        assertEquals(
                5,
                TestWorlds.load(save).items().entities().stream()
                        .mapToInt(ItemSnapshot::count)
                        .sum());
    }

    @Test
    void throwingWhileSprintingDoesNotInheritThePlayersVelocity() throws Exception {
        Path save = directory.resolve("throw-sprinting.vxl");
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(new WorldRuntime.Controls(
                    new PlayerInput(true, false, false, false, false, false, true), 0, 0, false, false, 0));
            await(runtime, f -> f.playerState().velocityX() > 4.5);
            assertTrue(runtime.offer(new WorldRuntime.DropItem(0, false, false, 0, 0)));
            var thrown = await(runtime, f -> !f.items().isEmpty());
            assertTrue(thrown.playerState().velocityX() > 4.5);
            double velocity = thrown.items().getFirst().item().vx();
            assertTrue(velocity > 3 && velocity < 4.2, "Throw inherited sprint velocity: " + velocity);
        }
    }

    @Test
    void dropsOneOrAWholeStackFromTheCommandSlotWithoutCollectingItImmediately() throws Exception {
        Path save = directory.resolve("drop-hotbar.vxl");
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 5);
        inventory.insert(BlockId.STONE, 3);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(controls(false, false));
            await(runtime, f -> f.tick() >= 2);
            assertTrue(runtime.offer(new WorldRuntime.DropItem(0, false, false, 0, 30)));
            assertTrue(runtime.offer(new WorldRuntime.SelectSlot(1)));
            var single = await(
                    runtime, f -> f.inventory().count(0) == 4 && f.inventory().selectedSlot() == 1);
            assertEquals(BlockId.DIRT, single.items().getFirst().item().itemId());
            assertEquals(1, single.items().getFirst().item().count());
            assertTrue(single.items().getFirst().item().vx() > 0);
            assertTrue(runtime.offer(new WorldRuntime.DropItem(1, true, false, 0, 30)));
            var whole = await(runtime, f -> f.inventory().count(1) == 0);
            assertEquals(
                    3,
                    whole.items().stream()
                            .map(i -> i.item())
                            .filter(i -> i.itemId() == BlockId.STONE)
                            .mapToInt(ItemSnapshot::count)
                            .sum());
            assertEquals(4, whole.inventory().count(0));
            assertTrue(runtime.offer(new WorldRuntime.DropItem(1, false, false, 0, 30)));
            var later = await(runtime, f -> f.tick() > whole.tick() + 3);
            assertEquals(
                    4, later.items().stream().mapToInt(i -> i.item().count()).sum());
        }
    }

    @Test
    void inventoryDropsPreferTheAuthoritativeCursorEvenWhenGuiSnapshotPredatesTheClick() throws Exception {
        Path save = directory.resolve("drop-cursor.vxl");
        var inventory = new Inventory();
        inventory.creativePick(0, BlockId.DIRT);
        inventory.creativePick(1, BlockId.STONE);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        try (var runtime = start(new Flat(), save)) {
            await(runtime, f -> f.tick() >= 2);
            assertTrue(runtime.offer(new WorldRuntime.ClickSlot(0, true, false)));
            assertTrue(runtime.offer(new WorldRuntime.DropItem(1, false, true, 0, 30)));
            var single = await(runtime, f -> f.inventory().count(Inventory.CARRIED) == 31);
            assertEquals(32, single.inventory().count(0));
            assertEquals(64, single.inventory().count(1));
            assertEquals(BlockId.DIRT, single.items().getFirst().item().itemId());
            assertTrue(runtime.offer(new WorldRuntime.DropItem(1, true, true, 0, 30)));
            var cursorEmpty = await(runtime, f -> f.inventory().count(Inventory.CARRIED) == 0);
            assertEquals(64, cursorEmpty.inventory().count(1));
            assertEquals(
                    32,
                    cursorEmpty.items().stream().mapToInt(i -> i.item().count()).sum());
            assertTrue(runtime.offer(new WorldRuntime.DropItem(1, true, true, 0, 30)));
            var slotEmpty = await(runtime, f -> f.inventory().count(1) == 0);
            assertEquals(
                    96,
                    slotEmpty.items().stream().mapToInt(i -> i.item().count()).sum());
            assertEquals(32, slotEmpty.inventory().count(0));
        }
    }

    @Test
    void pauseAndBlockedGameplayRejectDropsWhileCreativeDropsConsumeActualStacks() throws Exception {
        Path save = directory.resolve("drop-guards.vxl");
        var inventory = new Inventory();
        inventory.insert(BlockId.DIRT, 5);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(controls(true, true));
            var paused = await(runtime, WorldRuntime.Frame::paused);
            assertTrue(runtime.offer(new WorldRuntime.DropItem(0, true, true, 0, 30)));
            var ignored = await(runtime, f -> f.publishedAt() > paused.publishedAt() + 50_000_000);
            assertEquals(5, ignored.inventory().count(0));
            assertTrue(ignored.items().isEmpty());
            runtime.controls(controls(true, false));
            var unpaused = await(runtime, f -> !f.paused());
            assertTrue(runtime.offer(new WorldRuntime.DropItem(0, true, false, 0, 30)));
            var blocked = await(runtime, f -> f.tick() > unpaused.tick() + 3);
            assertEquals(5, blocked.inventory().count(0));
            assertTrue(blocked.items().isEmpty());
            runtime.controls(controls(false, false));
            await(runtime, f -> f.tick() > blocked.tick() + 1);
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(runtime, f -> f.mode() == PlayerController.Mode.CREATIVE && !f.modePending());
            assertTrue(runtime.offer(new WorldRuntime.DropItem(0, false, false, 0, 30)));
            var creative = await(runtime, f -> f.inventory().count(0) == 4);
            assertEquals(
                    1, creative.items().stream().mapToInt(i -> i.item().count()).sum());
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            var spectator = await(runtime, f -> f.mode() == PlayerController.Mode.SPECTATOR && !f.modePending());
            assertTrue(runtime.offer(new WorldRuntime.DropItem(0, true, true, 0, 30)));
            var later = await(runtime, f -> f.tick() > spectator.tick() + 3);
            assertEquals(4, later.inventory().count(0));
            assertEquals(
                    1, later.items().stream().mapToInt(i -> i.item().count()).sum());
        }
        assertThrows(
                IllegalArgumentException.class, () -> new WorldRuntime.DropItem(Inventory.CARRIED, false, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldRuntime.DropItem(0, false, true, 0, Double.NaN));
    }

    @Test
    void restartPreservesPartialPickupDormantEntitiesAndTimersInTheSameWorldSnapshot() throws Exception {
        Path save = directory.resolve("items-restart.vxl");
        byte[] blocks = new byte[37];
        int[] counts = new int[37];
        java.util.Arrays.fill(blocks, 0, Inventory.SLOTS, BlockId.DIRT);
        java.util.Arrays.fill(counts, 0, Inventory.SLOTS, 64);
        counts[0] = 63;
        var inventory = new com.norbertfila.player.InventorySnapshot(blocks, counts, 0);
        var base = TestWorlds.capture(42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory);
        var dormant = new ItemSnapshot(2, BlockId.COBBLE, 4, 160.5, 2, 8.5, 0, -1, 0, 8, 123, false);
        var items = new ItemState(
                3,
                java.util.List.of(new ItemSnapshot(1, BlockId.DIRT, 10, .5, 1.125, .5, 0, 0, 0, 0, 40, true), dormant));
        TestWorlds.save(
                save,
                new com.norbertfila.persistence.world.WorldSnapshot(
                        base.identity(),
                        base.sequence(),
                        base.lastSavedAt(),
                        base.deltas(),
                        base.fluids(),
                        base.inventory(),
                        base.player(),
                        items));
        try (var runtime = start(new Flat(), save)) {
            var picked = await(
                    runtime, f -> f.inventory().count(0) == 64 && !f.items().isEmpty());
            assertEquals(9, picked.items().getFirst().item().count());
            runtime.controls(controls(true, true));
            var paused = await(runtime, WorldRuntime.Frame::paused);
            long published = paused.publishedAt();
            var later = await(runtime, f -> f.publishedAt() > published + 50_000_000);
            assertEquals(paused.items(), later.items());
        }
        var saved = TestWorlds.load(save);
        assertEquals(64, saved.inventory().count(0));
        assertEquals(9, saved.items().entities().getFirst().count());
        assertEquals(dormant, saved.items().entities().getLast());
        int age = saved.items().entities().getFirst().ageTicks();
        try (var runtime = start(new Flat(), save)) {
            var frame = await(
                    runtime,
                    f -> !f.items().isEmpty() && f.items().getFirst().item().ageTicks() > age);
            assertEquals(9, frame.items().getFirst().item().count());
            assertEquals(64, frame.inventory().count(0));
        }
        assertEquals(dormant, TestWorlds.load(save).items().entities().getLast());
    }

    @TempDir
    Path directory;

    @Test
    void survivalTapDoesNotMineAndReleasePauseAndLookingAwayCancelProgress() throws Exception {
        Path save = directory.resolve("survival-mining.vxl");
        World world = new World();
        BlockPos block = new BlockPos(3, 2, 0);
        world.setBlock(block, BlockId.STONE, 64);
        TestWorlds.save(save, 42, 1, world);
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(breakingControls(false, false, false));
            long before = await(runtime, f -> f.target().isPresent()).tick();
            assertTrue(runtime.offer(new WorldRuntime.Edit(true, BlockId.AIR, 0, 0)));
            var tapped = await(runtime, f -> f.tick() > before + 65);
            assertEquals(block, tapped.target().orElseThrow().block());
            assertTrue(tapped.breaking().isEmpty());
            assertTrue(tapped.brokenBlocks().isEmpty());
            assertEquals(0, tapped.inventory().count(0));

            runtime.controls(breakingControls(true, false, false));
            var mining = await(
                    runtime,
                    f -> f.breaking().isPresent() && f.breaking().orElseThrow().fraction() >= .25);
            assertEquals(block, mining.breaking().orElseThrow().block());
            assertEquals(0, mining.inventory().count(0));
            assertTrue(mining.brokenBlocks().isEmpty());
            assertTrue(runtime.offer(new WorldRuntime.Edit(true, BlockId.AIR, 0, 0)));
            await(
                    runtime,
                    f -> f.publishedAt() > mining.publishedAt()
                            && f.breaking().isPresent()
                            && f.breaking().orElseThrow().fraction() < .25);
            runtime.controls(breakingControls(false, false, false));
            await(runtime, f -> f.breaking().isEmpty() && f.target().isPresent());

            runtime.controls(breakingControls(true, false, false));
            var restarted = await(runtime, f -> f.breaking().isPresent());
            assertTrue(restarted.breaking().orElseThrow().fraction() < .25);
            runtime.controls(breakingControls(true, false, true));
            await(runtime, f -> f.paused() && f.breaking().isEmpty());
            runtime.controls(breakingControls(true, false, false));
            var resumed = await(runtime, f -> !f.paused() && f.breaking().isPresent());
            assertTrue(resumed.breaking().orElseThrow().fraction() < .25);

            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 90, 0, false, false, 0, 0, false, 0, 0, true));
            await(
                    runtime,
                    f -> f.yaw() == 90 && f.target().isEmpty() && f.breaking().isEmpty());
            runtime.controls(breakingControls(true, false, false));
            await(runtime, f -> f.breaking().isPresent());
            assertTrue(runtime.offer(new WorldRuntime.Fill(block, block, BlockId.DIRT)));
            var replaced = await(
                    runtime,
                    f -> f.target().isPresent()
                            && f.target().orElseThrow().blockId() == BlockId.DIRT
                            && f.breaking().isPresent());
            assertTrue(replaced.breaking().orElseThrow().fraction() < .25);
            var mined = await(runtime, f -> !f.items().isEmpty());
            assertEquals(BlockId.DIRT, mined.items().getFirst().item().itemId());
            assertEquals(0, mined.inventory().count(0));
            assertTrue(mined.target().isEmpty());
            assertTrue(mined.breaking().isEmpty());
            assertEquals(1, mined.brokenBlocks().size());
            var event = mined.brokenBlocks().getFirst();
            assertEquals(block, event.block());
            assertEquals(BlockId.DIRT, event.blockId());
            assertTrue(event.tick() > replaced.tick() && event.tick() <= mined.tick());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> mined.brokenBlocks().clear());
            var later = await(runtime, f -> f.tick() > mined.tick() + 3);
            assertEquals(mined.brokenBlocks(), later.brokenBlocks());
        }
    }

    @Test
    void creativeClickRemovesImmediatelyWithoutHoldingOrCollectingAndMiningWinsOverPlacement() throws Exception {
        Path save = directory.resolve("creative-mining.vxl");
        World world = new World();
        BlockPos block = new BlockPos(3, 2, 0);
        world.setBlock(block, BlockId.STONE, 64);
        TestWorlds.save(save, 42, 1, world);
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(breakingControls(false, false, false));
            await(runtime, f -> f.target().isPresent());
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(
                    runtime,
                    f -> f.mode() == PlayerController.Mode.CREATIVE
                            && !f.modePending()
                            && f.target().isPresent());
            assertTrue(runtime.offer(new WorldRuntime.PickCreative(0, BlockId.DIRT)));
            long before = await(
                            runtime,
                            f -> f.inventory().count(0) == 64 && f.target().isPresent())
                    .tick();
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 0, 1, true, 0, 0, true));
            assertTrue(runtime.offer(new WorldRuntime.Edit(true, BlockId.AIR, 0, 0)));
            var removed = await(runtime, f -> f.target().isEmpty());
            assertTrue(removed.tick() < before + 60);
            assertTrue(removed.breaking().isEmpty());
            assertEquals(1, removed.brokenBlocks().size());
            assertEquals(block, removed.brokenBlocks().getFirst().block());
            assertEquals(BlockId.STONE, removed.brokenBlocks().getFirst().blockId());
            assertEquals(64, removed.inventory().count(0));
            assertEquals(BlockId.DIRT, removed.inventory().block(0));
        }
        World restored = new World();
        TestWorlds.restore(restored, TestWorlds.load(save), 64);
        assertEquals(BlockId.AIR, restored.changes().get(block).orElseThrow());
        assertTrue(restored.changes().get(new BlockPos(2, 2, 0)).isEmpty());
    }

    @Test
    void survivalMiningBlocksSimultaneousPlacementAndPlacementConsumesOnlyAfterRelease() throws Exception {
        Path save = directory.resolve("mining-placement.vxl");
        World world = new World();
        BlockPos anchor = new BlockPos(3, 2, 0), placed = new BlockPos(2, 2, 0);
        world.setBlock(anchor, BlockId.STONE, 64);
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        TestWorlds.save(
                save,
                TestWorlds.capture(42, 1, world, com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(breakingControls(false, false, false));
            await(runtime, f -> f.target().isPresent());
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 0, 1, true, 0, 0, true));
            var mining = await(runtime, f -> f.breaking().isPresent());
            assertEquals(anchor, mining.target().orElseThrow().block());
            assertEquals(1, mining.inventory().count(0));
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 0, 2, true, 0, 0, false));
            var result = await(runtime, f -> f.inventory().count(0) == 0);
            assertEquals(placed, result.target().orElseThrow().block());
            assertEquals(BlockId.DIRT, result.target().orElseThrow().blockId());
            assertTrue(result.breaking().isEmpty());
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 0, 2, false));
        }
        World restored = new World();
        TestWorlds.restore(restored, TestWorlds.load(save), 64);
        assertEquals(BlockId.DIRT, restored.changes().get(placed).orElseThrow());
        assertEquals(BlockId.STONE, restored.changes().get(anchor).orElseThrow());
    }

    @Test
    void completedSurvivalMiningDropsAnItemWhenInventoryIsFull() throws Exception {
        Path save = directory.resolve("mining-full.vxl");
        World world = new World();
        BlockPos block = new BlockPos(3, 2, 0);
        world.setBlock(block, BlockId.STONE, 64);
        byte[] blocks = new byte[Inventory.SLOTS + 1];
        int[] counts = new int[Inventory.SLOTS + 1];
        java.util.Arrays.fill(blocks, 0, Inventory.SLOTS, BlockId.DIRT);
        java.util.Arrays.fill(counts, 0, Inventory.SLOTS, Inventory.MAX_STACK);
        var inventory = new com.norbertfila.player.InventorySnapshot(blocks, counts, 0);
        TestWorlds.save(save, TestWorlds.capture(42, 1, world, com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory));
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(breakingControls(true, false, false));
            long before = await(runtime, f -> f.breaking().isPresent()).tick();
            var after = await(runtime, f -> f.tick() > before + 65);
            assertTrue(after.target().isEmpty());
            assertEquals(inventory, after.inventory());
            assertEquals(1, after.brokenBlocks().size());
            assertEquals(BlockId.COBBLE, after.items().getFirst().item().itemId());
        }
        assertEquals(
                BlockId.COBBLE,
                TestWorlds.load(save).items().entities().getFirst().itemId());
    }

    private static WorldRuntime.Controls breakingControls(boolean held, boolean blocked, boolean paused) {
        return new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, blocked, paused, 0, 0, false, 0, 0, held);
    }

    @Test
    void publishesStableTargetsAndClearsThemForBlockedPausedEditedAndSpectatorStates() throws Exception {
        Path save = directory.resolve("target.vxl");
        World world = new World();
        BlockPos near = new BlockPos(3, 2, 0), far = new BlockPos(6, 2, 0);
        world.setBlock(near, BlockId.STONE, 64);
        world.setBlock(far, BlockId.STONE, 64);
        TestWorlds.save(save, 42, 1, world);
        var active = new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 0);
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(active);
            var targeted = await(runtime, f -> f.target().isPresent());
            var hit = targeted.target().orElseThrow();
            assertEquals(near, hit.block());
            assertEquals(2.5, hit.distance());

            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 90, 0, false, false, 0));
            await(runtime, f -> f.yaw() == 90 && f.target().isEmpty());
            runtime.controls(active);
            await(runtime, f -> f.target().isPresent());

            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, true, false, 0));
            await(runtime, f -> f.target().isEmpty());
            runtime.controls(active);
            await(runtime, f -> f.target().isPresent());
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, true, 0));
            var paused = await(runtime, f -> f.paused() && f.target().isEmpty());
            runtime.controls(active);
            await(
                    runtime,
                    f -> !f.paused() && f.tick() > paused.tick() && f.target().isPresent());

            assertTrue(runtime.offer(new WorldRuntime.Edit(true, BlockId.AIR, 0, 0)));
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 0, 0, false, 0, 0, true));
            var mining = await(runtime, f -> f.breaking().isPresent());
            assertEquals(near, mining.breaking().orElseThrow().block());
            assertEquals(0, mining.inventory().count(0));
            await(runtime, f -> f.target().isEmpty() && !f.items().isEmpty());
            runtime.controls(active);
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            var creative = await(
                    runtime,
                    f -> f.mode() == PlayerController.Mode.CREATIVE
                            && f.target().isPresent());
            assertEquals(far, creative.target().orElseThrow().block());
            assertEquals(5.5, creative.target().orElseThrow().distance());
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(
                    runtime,
                    f -> f.mode() == PlayerController.Mode.SPECTATOR
                            && f.target().isEmpty());
            assertEquals(hit, targeted.target().orElseThrow());
        }
    }

    @Test
    void asynchronousStopDoesNotWaitForStartupAndRejectsNewCommands() throws Exception {
        var terrain = new Flat();
        terrain.blockSpawn = true;
        var save = directory.resolve("stop-startup.vxl");
        var runtime = start(terrain, save);
        try {
            assertTrue(terrain.workerEntered.await(3, TimeUnit.SECONDS));
            assertTimeout(java.time.Duration.ofMillis(100), runtime::requestStop);
            assertFalse(runtime.isStopped());
            assertFalse(runtime.offer(new WorldRuntime.NextMode()));
            terrain.release.countDown();
            awaitStopped(runtime);
        } finally {
            terrain.release.countDown();
            runtime.close();
        }
        assertEquals(42, TestWorlds.load(save).identity().seed());
        assertTrue(runtime.isStopped());
    }

    @Test
    void repeatedAsyncStopAndRestartPreserveInventoryAndWorldClock() throws Exception {
        var save = directory.resolve("cycles.vxl");
        var inventory = new Inventory();
        inventory.creativePick(0, BlockId.POPPY);
        inventory.click(0, true, false);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        long tick = 0;
        for (int cycle = 0; cycle < 3; cycle++) {
            var runtime = start(new Flat(), save);
            try {
                long previousTick = tick;
                var frame = await(runtime, f -> f.tick() > previousTick + 2);
                assertEquals(inventory.snapshot(), frame.inventory());
                runtime.controls(controls(true, true));
                var paused = await(runtime, WorldRuntime.Frame::paused);
                tick = paused.tick();
                runtime.requestStop();
                runtime.requestStop();
                awaitStopped(runtime);
            } finally {
                runtime.close();
            }
            var stored = TestWorlds.load(save);
            assertEquals(inventory.snapshot(), stored.inventory());
            assertEquals(tick, stored.fluids().tick());
        }
    }

    @Test
    void failedFinalSaveRetainsSnapshotUntilExplicitRetry() throws Exception {
        Path save = directory.resolve("retry.vxl"), backup = directory.resolve("previous.vxl");
        var runtime = start(new Flat(), save);
        await(runtime, f -> f.tick() >= 2);
        Files.move(save, backup);
        Files.createDirectory(save);
        runtime.requestStop();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (runtime.state() != WorldRuntime.State.SAVE_ERROR && System.nanoTime() < deadline) Thread.sleep(2);
            assertEquals(WorldRuntime.State.SAVE_ERROR, runtime.state());
            assertNotNull(runtime.retainedSnapshot());
            assertFalse(runtime.isStopped());
            Files.delete(save);
            Files.move(backup, save);
            runtime.retrySave();
            awaitStopped(runtime);
            assertEquals(runtime.retainedSnapshot(), TestWorlds.load(save));
        } finally {
            if (runtime.state() == WorldRuntime.State.SAVE_ERROR) runtime.discardChanges();
            runtime.close();
        }
    }

    private static void awaitStopped(WorldRuntime runtime) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!runtime.isStopped() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(runtime.isStopped(), "World thread did not finish its final save and shutdown");
    }

    @Test
    void vegetationAdmissionRemovesPlantsWhoseProceduralSupportWasEdited() throws Exception {
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        TerrainGenerator generator =
                new TerrainGenerator(42, new TerrainConfig(8, disabled, disabled, disabled), (x, z) -> 0);
        ChunkPos position = new ChunkPos(0, 0, 0);
        byte[] generated = generator.generate(position).blocks();
        BlockPos plant = null;
        for (int z = 0; z < 16 && plant == null; z++)
            for (int x = 0; x < 16; x++)
                if (PlantRules.isPlant(generated[(9 * 16 + z) * 16 + x])) {
                    plant = new BlockPos(x, 9, z);
                    break;
                }
        assertNotNull(plant);
        World world = new World();
        world.setBlock(new BlockPos(plant.x(), 8, plant.z()), BlockId.AIR, 64);
        Path save = directory.resolve("vegetation.vxl");
        TestWorlds.save(
                save,
                com.norbertfila.persistence.world.WorldSnapshots.capture(
                        TestWorlds.identity(generator.seed(), generator.generatorVersion()),
                        1,
                        world,
                        com.norbertfila.fluid.FluidSnapshot.EMPTY,
                        com.norbertfila.player.InventorySnapshot.EMPTY,
                        new com.norbertfila.persistence.world.SavedPlayerState(
                                .5, 9, .5, -90, -25, com.norbertfila.player.PlayerController.Mode.SURVIVAL, false)));
        try (WorldRuntime runtime = new WorldRuntime(
                generator,
                new com.norbertfila.meshing.WaterMesher(),
                new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE),
                save)) {
            var mesh = awaitMesh(runtime, position);
            runtime.acknowledge(mesh, true);
            await(runtime, frame -> frame.tick() >= 5);
        }
        World restored = new World();
        TestWorlds.restore(restored, TestWorlds.load(save), 64);
        assertEquals(BlockId.AIR, restored.changes().get(plant).orElseThrow());
    }

    @Test
    void ownerRunsWaterWhileControlsAreBlockedAndShutdownRestoresItsClock() throws Exception {
        Path save = directory.resolve("water.vxl");
        World world = new World();
        world.setBlock(new BlockPos(8, 1, 8), BlockId.WATER, 64);
        TestWorlds.save(save, 42, 1, world);
        Flat terrain = new Flat();
        long stopped;
        try (WorldRuntime runtime = start(terrain, save)) {
            var frame = await(runtime, f -> f.tick() >= 40);
            assertTrue(frame.pendingWater() > 0);
            assertThrows(
                    UnsupportedOperationException.class, () -> frame.residents().clear());
            assertTrue(terrain.threads.stream().anyMatch(name -> name.startsWith("chunk-generation-")));
            assertFalse(terrain.threads.contains(Thread.currentThread().getName()));
            stopped = frame.tick();
        }
        var saved = TestWorlds.load(save);
        assertTrue(saved.fluids().tick() >= stopped);
        assertTrue(saved.deltas().stream()
                .flatMap(d -> d.changes().stream())
                .anyMatch(c -> c.value() == BlockId.WATER && c.waterState() > WaterState.SOURCE));
        try (WorldRuntime restarted = start(new Flat(), save)) {
            assertTrue(await(restarted, f -> f.tick() > saved.fluids().tick()).tick()
                    > saved.fluids().tick());
        }
    }

    @Test
    void publishesActualFractionalContactWhileControlsAreBlocked() throws Exception {
        Path save = directory.resolve("contact.vxl");
        World world = new World();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, 64);
        TestWorlds.save(save, 42, 1, world);
        try (WorldRuntime runtime = new WorldRuntime(
                new Flat(), new WaterMesher(), new ViewDemand(1, 0, 1, ViewDemand.Shape.SQUARE), save)) {
            var frame = await(runtime, f -> f.tick() >= 40 && f.waterContact().available());
            assertEquals(
                    (8 / 9d) / PlayerState.STANDING_HEIGHT, frame.waterContact().submergedFraction(), 1e-7);
            assertFalse(frame.waterContact().eyesSubmerged());
            assertEquals(1, frame.playerState().position().y());
        }
    }

    @Test
    void swimmingCrossesChunkBoundaryWithSixtyFourMeshesAwaitingAck() throws Exception {
        Path save = directory.resolve("swim-backpressure.vxl");
        World world = new World();
        for (int x = -2; x <= 36; x++)
            for (int y = 1; y <= 3; y++)
                for (int z = -2; z <= 2; z++) world.setBlock(new BlockPos(x, y, z), BlockId.WATER, 64);
        TestWorlds.save(save, 42, 1, world);
        try (WorldRuntime runtime = new WorldRuntime(
                new Flat(), new WaterMesher(), new ViewDemand(4, 0, 0, ViewDemand.Shape.SQUARE), save)) {
            var held = new java.util.ArrayList<WorldRuntime.SessionMesh>();
            await(runtime, f -> {
                WorldRuntime.SessionMesh mesh;
                while ((mesh = runtime.pollMesh()) != null) held.add(mesh);
                return held.size() == 64 && f.waterContact().available();
            });
            runtime.controls(new WorldRuntime.Controls(
                    new PlayerInput(true, false, false, false, false, false, false), 0, 0, false, false, 4));
            var previous = await(runtime, f -> f.playerState().position().x() > 2);
            long end = previous.tick() + 390;
            while (previous.tick() < end) {
                long tick = previous.tick();
                var next = await(runtime, f -> f.tick() > tick);
                assertTrue(
                        next.playerState().position().x()
                                > previous.playerState().position().x(),
                        "Swimming stopped at tick " + next.tick());
                assertTrue(next.waterContact().available());
                previous = next;
            }
            assertTrue(previous.playerState().position().x() > 16);
            assertEquals(64, held.size());
        }
        assertTrue(TestWorlds.load(save).fluids().tick() > 390);
    }

    @Test
    void explicitPauseStopsWorldClockButBlockedControlsDoNot() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("pause.vxl"))) {
            long before = await(runtime, f -> f.tick() >= 3).tick();
            runtime.controls(controls(true, true));
            var paused = await(runtime, WorldRuntime.Frame::paused);
            long published = paused.publishedAt();
            var later = await(runtime, f -> f.publishedAt() > published + 100_000_000);
            assertEquals(paused.tick(), later.tick());
            runtime.controls(controls(true, false));
            assertTrue(await(runtime, f -> !f.paused() && f.tick() > paused.tick())
                            .tick()
                    > before);
        }
    }

    @Test
    void blockedGenerationKeepsLoadingResponsiveWithoutAdvancingSimulation() throws Exception {
        Flat terrain = new Flat();
        terrain.blockWorkers = true;
        try (WorldRuntime runtime = start(terrain, directory.resolve("slow.vxl"))) {
            assertTrue(terrain.workerEntered.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (runtime.frame() == null && System.nanoTime() < deadline) Thread.sleep(2);
            var first = runtime.frame();
            assertNotNull(first);
            Thread.sleep(80);
            var later = runtime.frame();
            assertTrue(later.publishedAt() > first.publishedAt());
            assertEquals(first.tick(), later.tick());
            assertFalse(later.ready());
            assertEquals(WorldRuntime.State.LOADING, runtime.state());
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
        } finally {
            terrain.release.countDown();
        }
    }

    @Test
    void slowStartupBoundsDiscreteActionsWithoutBlockingTheCaller() throws Exception {
        Flat terrain = new Flat();
        terrain.blockSpawn = true;
        WorldRuntime runtime = start(terrain, directory.resolve("bounded.vxl"));
        try {
            assertTrue(terrain.workerEntered.await(3, TimeUnit.SECONDS));
            for (int count = 0; count < 64; count++) assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            assertFalse(runtime.offer(new WorldRuntime.NextMode()));
            assertNull(runtime.frame());
        } finally {
            terrain.release.countDown();
            runtime.close();
        }
    }

    @Test
    void stalledRendererBoundsMeshPublicationWithoutStoppingWorldTicks() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("backpressure.vxl"))) {
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, true, false, 3));
            var blocked = await(runtime, f -> f.tick() >= 60);
            assertTrue(blocked.metrics().streaming().meshOutstanding() <= 64);
            assertTrue(blocked.metrics().outstandingAcks() <= 64);
            assertTrue(blocked.metrics().outstandingMeshBytes() >= 0);
            var held = new java.util.ArrayList<WorldRuntime.SessionMesh>();
            WorldRuntime.SessionMesh mesh;
            while ((mesh = runtime.pollMesh()) != null) {
                held.add(mesh);
            }
            assertTrue(!held.isEmpty() && held.size() <= 64);
            held.forEach(result -> runtime.acknowledge(result, true));
            assertTrue(await(runtime, f -> {
                                var next = runtime.pollMesh();
                                if (next == null) return false;
                                runtime.acknowledge(next, true);
                                return true;
                            })
                            .tick()
                    > blocked.tick());
        }
    }

    @Test
    void lateSuccessfulUploadIsRejectedAfterEditAndFreshMeshCanBeAcknowledged() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("edit.vxl"))) {
            runtime.controls(controls(false, false));
            var old = awaitSettledMesh(runtime, new ChunkPos(0, 0, 0));
            assertTrue(runtime.frame().isCurrent(old));
            assertTrue(runtime.offer(new WorldRuntime.Edit(true, BlockId.AIR, 0, -90)));
            runtime.controls(
                    new WorldRuntime.Controls(PlayerInput.NONE, 0, -90, false, false, 0, 0, false, 0, 0, true));
            await(runtime, f -> !f.isCurrent(old));
            runtime.controls(controls(false, false));
            runtime.acknowledge(old, true);
            var replacement = awaitMesh(runtime, old.mesh().position());
            assertNotEquals(old.mesh().token(), replacement.mesh().token());
            assertTrue(replacement.mesh().dataRevision() > old.mesh().dataRevision());
            runtime.acknowledge(replacement, true);
            await(runtime, f -> !f.validUploads().containsKey(replacement.mesh().token()));
        }
    }

    @Test
    void queuedJumpEdgesPreserveCreativeDoubleTap() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("input.vxl"))) {
            runtime.controls(controls(false, false));
            await(runtime, f -> !f.residents().isEmpty());
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(runtime, f -> f.mode() == PlayerController.Mode.CREATIVE);
            assertTrue(runtime.offer(new WorldRuntime.Jump(10)));
            assertTrue(runtime.offer(new WorldRuntime.Jump(10.1)));
            assertTrue(await(runtime, WorldRuntime.Frame::flying).flying());
        }
    }

    @Test
    void heldForwardFlightCrossesUnviewedChunksEvenWithoutRendererAcknowledgements() throws Exception {
        try (WorldRuntime runtime = new WorldRuntime(
                new Flat(),
                new WaterMesher(),
                new ViewDemand(4, 0, 0, ViewDemand.Shape.SQUARE),
                TestWorlds.prepare(directory.resolve("continuous.vxl"), 42, 1))) {
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 4));
            var heldMeshes = new java.util.ArrayList<WorldRuntime.SessionMesh>();
            await(runtime, f -> {
                WorldRuntime.SessionMesh mesh;
                while ((mesh = runtime.pollMesh()) != null) heldMeshes.add(mesh);
                return heldMeshes.size() == 64;
            });
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(runtime, f -> f.mode() == PlayerController.Mode.CREATIVE);
            assertTrue(runtime.offer(new WorldRuntime.Jump(10)));
            assertTrue(runtime.offer(new WorldRuntime.Jump(10.1)));
            var start = await(runtime, WorldRuntime.Frame::flying);
            runtime.controls(new WorldRuntime.Controls(
                    new PlayerInput(true, false, false, false, true, false, true), 0, 0, false, false, 4));
            var previous = await(runtime, f -> f.tick() > start.tick() + 30);
            long end = previous.tick() + 120;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (previous.tick() < end && System.nanoTime() < deadline) {
                long tick = previous.tick();
                var next = await(runtime, f -> f.tick() > tick);
                assertTrue(
                        next.playerState().position().x()
                                > previous.playerState().position().x(),
                        "Held forward stopped at tick " + next.tick());
                previous = next;
            }
            assertTrue(previous.tick() >= end);
            assertTrue(previous.playerState().position().y() > 16);
            assertEquals(64, heldMeshes.size());
        }
    }

    @Test
    void heldPlacementRetriesAgainstTheNextExposedFace() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        PlayerController player = new PlayerController();
        player.setPosition(new PlayerState.Position(5.5, 1, .5));
        var controls = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, false, false, 0, 0, true);
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        inventory.add(BlockId.DIRT);

        assertTrue(WorldRuntime.placeInventory(interaction, player, controls, inventory)
                .isPresent());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(2, 2, 0)).orElseThrow());
        assertTrue(WorldRuntime.placeInventory(interaction, player, controls, inventory)
                .isPresent());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(3, 2, 0)).orElseThrow());
    }

    @Test
    void inventoryPlacementConsumesOnlySuccessfulSurvivalChangesAndMiningDropsItems() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        var interaction = new BlockInteraction(world, 8);
        var player = new PlayerController();
        player.setPosition(new PlayerState.Position(5.5, 1, .5));
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        var blocked = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, true, false, 0, 0, true);
        assertTrue(WorldRuntime.placeInventory(interaction, player, blocked, inventory)
                .isEmpty());
        assertEquals(1, inventory.snapshot().count(0));
        var active = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, false, false, 0, 0, true);
        assertTrue(WorldRuntime.placeInventory(interaction, player, active, inventory)
                .orElseThrow()
                .changed());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(2, 2, 0)).orElseThrow());
        assertEquals(0, inventory.snapshot().count(0));
        assertTrue(WorldRuntime.placeInventory(interaction, player, active, inventory)
                .isEmpty());
        var remove = new WorldRuntime.Edit(true, BlockId.AIR, 180, 0);
        var items = new ItemSystem();
        assertTrue(WorldRuntime.remove(interaction, player, remove, items)
                .orElseThrow()
                .changed());
        assertEquals(0, inventory.snapshot().count(0));
        assertEquals(BlockId.DIRT, items.snapshot().entities().getFirst().itemId());
        inventory.creativePick(0, BlockId.DIRT);
        for (int i = 1; i < 9; i++) inventory.creativePick(i, BlockId.DIRT);
        for (int i = 9; i < 36; i++) {
            inventory.click(0, false, true);
            inventory.creativePick(0, BlockId.DIRT);
        }
        assertTrue(WorldRuntime.remove(interaction, player, remove, items)
                .orElseThrow()
                .changed());
        assertEquals(BlockId.AIR, world.getBlock(new BlockPos(1, 2, 0)).orElseThrow());
        assertEquals(BlockId.COBBLE, items.snapshot().entities().getLast().itemId());
        world.setBlock(new BlockPos(1, 2, 0), BlockId.STONE, 8);
        assertTrue(player.setMode(world, PlayerController.Mode.CREATIVE));
        assertTrue(WorldRuntime.placeInventory(interaction, player, active, inventory)
                .orElseThrow()
                .changed());
        assertEquals(64, inventory.snapshot().count(0));
    }

    @Test
    void inventoryCommandsWorkWhileMenuBlocksGameplayAndPersistAcrossRestart() throws Exception {
        Path save = directory.resolve("inventory-runtime.vxl");
        var inventory = new Inventory();
        inventory.creativePick(0, BlockId.POPPY);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(controls(true, false));
            await(runtime, frame -> frame.inventory().count(0) == 64);
            assertTrue(runtime.offer(new WorldRuntime.ClickSlot(0, true, false)));
            assertTrue(runtime.offer(new WorldRuntime.PickCreative(1, BlockId.BEDROCK)));
            assertTrue(runtime.offer(new WorldRuntime.SelectSlot(8)));
            await(
                    runtime,
                    frame -> frame.inventory().count(36) == 32
                            && frame.inventory().selectedSlot() == 8);
            assertEquals(0, runtime.frame().inventory().count(1));
        }
        try (var runtime = start(new Flat(), save)) {
            var restored = await(runtime, frame -> frame.inventory().count(36) == 32);
            assertEquals(8, restored.inventory().selectedSlot());
            assertEquals(32, restored.inventory().count(0));
            assertTrue(runtime.offer(new WorldRuntime.ReturnCarried()));
            await(
                    runtime,
                    frame -> frame.inventory().count(36) == 0
                            && frame.inventory().count(0) == 64);
            long unblockedAt = runtime.frame().tick();
            runtime.controls(controls(false, false));
            await(runtime, frame -> frame.tick() > unblockedAt + 1);
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(runtime, frame -> frame.mode() == PlayerController.Mode.CREATIVE && !frame.modePending());
            runtime.controls(controls(true, false));
            assertTrue(runtime.offer(new WorldRuntime.PickCreative(1, BlockId.BEDROCK)));
            await(runtime, frame -> frame.inventory().count(1) == 64);
            long resumedAt = runtime.frame().tick();
            runtime.controls(controls(false, false));
            await(runtime, frame -> frame.tick() > resumedAt + 1);
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            var spectator = await(runtime, frame -> frame.mode() == PlayerController.Mode.SPECTATOR);
            runtime.controls(controls(true, false));
            assertTrue(runtime.offer(new WorldRuntime.ClickSlot(1, false, false)));
            assertTrue(runtime.offer(new WorldRuntime.PickCreative(2, BlockId.STONE)));
            var later = await(runtime, frame -> frame.tick() > spectator.tick() + 3);
            assertEquals(spectator.inventory(), later.inventory());
        }
    }

    @Test
    void heldPlacementDoesNothingWhenIntentIsBlocked() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        PlayerController player = new PlayerController();
        player.setPosition(new PlayerState.Position(5.5, 1, .5));
        var controls = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, true, false, 0, 0, true);
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);

        assertTrue(WorldRuntime.placeInventory(interaction, player, controls, inventory)
                .isEmpty());
        assertEquals(BlockId.AIR, world.getBlock(new BlockPos(2, 2, 0)).orElseThrow());
        assertEquals(1, inventory.snapshot().count(0));
    }

    @Test
    void wrongGeneratorCannotOverwriteAnExistingSave() throws Exception {
        Path save = directory.resolve("wrong.vxl");
        TestWorlds.save(save, 99, 1, new World());
        byte[] before = Files.readAllBytes(save);
        WorldRuntime runtime = start(new Flat(), save);
        assertThrows(IllegalStateException.class, runtime::close);
        assertArrayEquals(before, Files.readAllBytes(save));
    }

    private WorldRuntime start(Flat terrain, Path save) throws Exception {
        return new WorldRuntime(
                terrain,
                new WaterMesher(),
                new ViewDemand(0, 0, 1, ViewDemand.Shape.SQUARE),
                TestWorlds.prepare(save, terrain.seed(), terrain.generatorVersion()));
    }

    @Test
    void controlsMustRemainRepresentableInSavedPlayerState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRuntime.Controls(PlayerInput.NONE, Double.MAX_VALUE, 0, false, false, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRuntime.Controls(PlayerInput.NONE, 0, 91, false, false, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRuntime.Controls(PlayerInput.NONE, 0, Double.NaN, false, false, 0));
    }

    private static WorldRuntime.Controls controls(boolean blocked, boolean paused) {
        return new WorldRuntime.Controls(PlayerInput.NONE, -90, 0, blocked, paused, 0);
    }

    private static WorldRuntime.Frame await(WorldRuntime runtime, Predicate<WorldRuntime.Frame> predicate)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            var frame = runtime.frame();
            if (frame != null && frame.ready()) runtime.activate();
            if (frame != null && runtime.state() == WorldRuntime.State.PLAYING && predicate.test(frame)) return frame;
            Thread.sleep(2);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Runtime did not publish expected state");
    }

    /** Waits for a mesh that stays current after neighbors finish loading, skipping provisional meshes. */
    private static WorldRuntime.SessionMesh awaitSettledMesh(WorldRuntime runtime, ChunkPos position) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var mesh = awaitMesh(runtime, position);
            long published = runtime.frame().tick();
            await(runtime, f -> f.tick() >= published + 10);
            if (runtime.frame().isCurrent(mesh)) return mesh;
            runtime.acknowledge(mesh, false);
        }
        throw new AssertionError("Runtime did not settle a mesh for " + position);
    }

    private static WorldRuntime.SessionMesh awaitMesh(WorldRuntime runtime, ChunkPos position) throws Exception {
        await(runtime, WorldRuntime.Frame::ready);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            runtime.frame();
            var mesh = runtime.pollMesh();
            if (mesh != null) {
                if (mesh.mesh().position().equals(position)) return mesh;
                runtime.acknowledge(mesh, true);
            }
            Thread.sleep(2);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Runtime did not publish expected mesh");
    }

    private static final class Flat implements TerrainSource {
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final CountDownLatch workerEntered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean blockWorkers;
        volatile boolean blockSpawn;

        public long seed() {
            return 42;
        }

        public int generatorVersion() {
            return 1;
        }

        public int surfaceHeight(int x, int z) {
            return 0;
        }

        public TerrainSummary sample(int x, int z) {
            threads.add(Thread.currentThread().getName());
            return new TerrainSummary(0, BlockId.STONE, BlockId.STONE, BlockId.STONE);
        }

        public ChunkData generate(ChunkPos position) {
            String thread = Thread.currentThread().getName();
            threads.add(thread);
            if ((blockWorkers && !thread.equals("voxel-world")) || (blockSpawn && thread.equals("voxel-world"))) {
                workerEntered.countDown();
                try {
                    release.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] blocks = new byte[Chunk.BLOCK_BYTES];
            for (int y = 0; y < Chunk.SIZE; y++)
                if ((long) position.y() * Chunk.SIZE + y <= 0)
                    for (int z = 0; z < Chunk.SIZE; z++)
                        for (int x = 0; x < Chunk.SIZE; x++)
                            blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = BlockId.STONE;
            return new ChunkData(position, blocks);
        }
    }
}
