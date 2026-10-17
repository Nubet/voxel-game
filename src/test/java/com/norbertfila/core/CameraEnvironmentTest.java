package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.interaction.VoxelRaycaster;
import com.norbertfila.items.ItemSystem;
import com.norbertfila.items.ItemThrower;
import com.norbertfila.player.*;
import com.norbertfila.player.visual.PlayerAnimator;
import com.norbertfila.player.visual.PlayerRenderStateExtractor;
import com.norbertfila.player.visual.model.PlayerModel;
import com.norbertfila.world.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CameraEnvironmentTest {
    private static final ChunkPos CENTER = new ChunkPos(0, 0, 0);
    private static final PlayerState.Position EYE = new PlayerState.Position(8.5, 8.5, 8.5);

    @Test
    void cardinalFrontViewKeepsLogicalMovementLookAndHeadInputs() {
        var environment = environment(new Chunk());
        for (float yaw : new float[] {-90, 0, 90, 180}) {
            var camera = new Camera();
            camera.restoreLook(yaw, 23);
            var forward = new Vector3f(camera.direction());
            camera.cyclePerspective();
            environment.position(camera, EYE, 1280, 720);
            assertEquals(4, distance(camera, EYE), 1e-6);
            assertTrue((camera.x() - EYE.x()) * forward.x + (camera.z() - EYE.z()) * forward.z < 0);
            camera.cyclePerspective();
            environment.position(camera, EYE, 1280, 720);
            assertEquals(yaw, camera.yaw());
            assertEquals(23, camera.pitch());
            assertEquals(-1, camera.direction().dot(forward), 1e-6);
            var player = new PlayerController();
            player.setMode(null, PlayerController.Mode.SPECTATOR);
            player.setPosition(EYE);
            var visual = new PlayerRenderStateExtractor()
                    .extract(player, yaw, 23, new WaterContact.Result(0, false, true), true);
            assertEquals(
                    PlayerAnimator.pose(visual, 1).transform(PlayerModel.Part.HEAD),
                    PlayerAnimator.pose(visual, 1, camera.yaw(), camera.pitch()).transform(PlayerModel.Part.HEAD));
            var world = new World();
            world.putChunk(CENTER, new Chunk());
            var target = new BlockPos(
                    (int) Math.floor(EYE.x() + forward.x * 2), (int) Math.floor(EYE.y() + forward.y * 2), (int)
                            Math.floor(EYE.z() + forward.z * 2));
            world.setBlock(target, BlockId.STONE, 64);
            double yr = Math.toRadians(camera.yaw()), pr = Math.toRadians(camera.pitch());
            assertEquals(
                    target,
                    new VoxelRaycaster()
                            .cast(
                                    world,
                                    EYE.x(),
                                    EYE.y(),
                                    EYE.z(),
                                    Math.cos(yr) * Math.cos(pr),
                                    Math.sin(pr),
                                    Math.sin(yr) * Math.cos(pr),
                                    3)
                            .orElseThrow()
                            .block());
            var inventory = new Inventory();
            inventory.add(BlockId.DIRT);
            var items = new ItemSystem();
            assertTrue(new ItemThrower(world, inventory, items).drop(0, false, EYE, camera.yaw(), camera.pitch()));
            var item = items.snapshot().entities().getFirst();
            assertEquals(EYE.x() + forward.x * .3, item.x(), 1e-7);
            assertEquals(EYE.y() + forward.y * .3, item.y(), 1e-7);
            assertEquals(EYE.z() + forward.z * .3, item.z(), 1e-7);
            player.tick(
                    new PlayerInput(true, false, false, false, false, false, false), .05, camera.yaw(), camera.pitch());
            var position = player.state().position();
            assertTrue((position.x() - EYE.x()) * forward.x + (position.z() - EYE.z()) * forward.z > 0);
            camera.updateLook(2, 1);
            assertTrue(camera.yaw() > yaw);
            assertTrue(camera.pitch() < 23);
            camera.cyclePerspective();
            environment.position(camera, EYE, 1280, 720);
            assertEquals(Camera.Perspective.FIRST, camera.perspective());
            assertEquals(0, distance(camera, EYE));
        }
    }

    @Test
    void segmentHitsExpandedCornersAndUsesPartialBlockBounds() {
        var chunk = new Chunk();
        chunk.set(10, 8, 9, BlockId.STONE);
        var environment = environment(chunk);
        assertTrue(environment.clip(EYE, 4, 0, 0, .6) < .4, "expanded corner must block a center ray that misses");
        assertEquals(1, environment.clip(EYE, 4, 0, 0, .1));
        chunk.set(10, 8, 9, BlockId.AIR);
        chunk.set(10, 8, 8, BlockId.SNOW_LAYER);
        assertEquals(1, environment(chunk).clip(EYE, 4, 0, 0, .1));
        chunk.set(10, 8, 8, BlockId.STONE);
        assertEquals((1.5 - .1) / 4 - .001 / 4, environment(chunk).clip(EYE, 4, 0, 0, .1), 1e-8);
        chunk.set(8, 8, 8, BlockId.STONE);
        assertEquals(0, environment(chunk).clip(EYE, 4, 0, 0, .1));
    }

    @Test
    void unknownAndOutOfSnapshotRetractAndSnapshotsStayImmutable() {
        var chunk = new Chunk();
        var environment = environment(chunk);
        var edge = new PlayerState.Position(15, 8, 8);
        assertEquals((1 - .2) / 4 - .001 / 4, environment.clip(edge, 4, 0, 0, .2), 1e-8);
        assertEquals(0, environment.clip(new PlayerState.Position(40, 8, 8), 4, 0, 0, .2));
        chunk.set(10, 8, 8, BlockId.STONE);
        assertEquals(1, environment.clip(EYE, 4, 0, 0, .2));
        assertTrue(environment(chunk).clip(EYE, 4, 0, 0, .2) < 1);
        assertEquals(0, new CameraEnvironment(CENTER, null).clip(EYE, 4, 0, 0, .2));
    }

    @Test
    void nearPlaneClearanceIncludesFovAspectAndPitch() {
        var chunk = new Chunk();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) chunk.set(11, y, z, BlockId.STONE);
        var environment = environment(chunk);
        var camera = new Camera();
        camera.restoreLook(180, 0);
        camera.cyclePerspective();
        environment.position(camera, EYE, 500, 500);
        double square = distance(camera, EYE);
        environment.position(camera, EYE, 2000, 500);
        assertTrue(distance(camera, EYE) < square);
        double t = Math.tan(Math.toRadians(camera.fieldOfView() / 2));
        double radius = .1 * Math.sqrt(1 + t * t * 17) + .02;
        assertTrue(camera.x() + radius < 11);
        camera.restoreLook(180, -45);
        environment.position(camera, EYE, 2000, 500);
        assertTrue(camera.x() + radius < 11);
        camera.restoreLook(180, 0);
        camera.updateZoom(true, 1);
        environment.position(camera, EYE, 2000, 500);
        assertTrue(distance(camera, EYE) > square);
        environment.position(camera, EYE, 100_000, 1);
        assertEquals(0, distance(camera, EYE));
    }

    @Test
    void visibilityFollowsFinalCameraInsteadOfDryPlayerEyeAndKeepsUnknown() {
        var chunk = new Chunk();
        chunk.set(4, 8, 8, BlockId.WATER);
        var environment = environment(chunk);
        var camera = new Camera();
        camera.restoreLook(0, 0);
        camera.cyclePerspective();
        environment.position(camera, EYE, 1280, 720);
        assertFalse(environment.medium(EYE.x(), EYE.y(), EYE.z()).submerged());
        var medium = environment.medium(camera.x(), camera.y(), camera.z());
        assertTrue(medium.submerged());
        var visibility = new com.norbertfila.render.UnderwaterVisibility();
        visibility.update(medium.submerged(), medium.available(), 1);
        assertEquals(1, visibility.amount());
        var unknown = environment.medium(40, 8, 8);
        visibility.update(unknown.submerged(), unknown.available(), 1);
        assertEquals(1, visibility.amount());
        camera.cyclePerspective();
        environment.position(camera, EYE, 1280, 720);
        medium = environment.medium(camera.x(), camera.y(), camera.z());
        visibility.update(medium.submerged(), medium.available(), 1);
        assertEquals(0, visibility.amount());
    }

    @Test
    void cameraWaterUsesTriangulatedSurfaceAndPreservesUnknown() {
        var chunk = new Chunk();
        chunk.set(8, 8, 8, BlockId.WATER);
        chunk.set(9, 8, 8, BlockId.WATER, (byte) 5);
        var environment = environment(chunk);
        var corners = WaterSurface.corners(environment.blocks()::waterSample, 8, 8, 8);
        double height = corners.heightAt(.75, .6);
        assertNotEquals(corners.h00(), corners.h11());
        assertEquals(new CameraEnvironment.Medium(true, true), environment.medium(8.75, 8 + height - .0001, 8.6));
        assertEquals(new CameraEnvironment.Medium(false, true), environment.medium(8.75, 8 + height + .0001, 8.6));
        chunk.set(0, 8, 8, BlockId.WATER);
        assertFalse(environment(chunk).medium(.5, 8.1, 8.5).available());
        assertFalse(environment.medium(16.5, 8, 8).available());
        assertEquals(new CameraEnvironment.Medium(false, true), environment.medium(8, 10, 8));
    }

    @Test
    void doubleCoordinatesKeepSubBlockCameraAndMediumPrecision() {
        var chunk = new Chunk();
        chunk.set(10, 8, 8, BlockId.STONE);
        chunk.set(7, 8, 8, BlockId.WATER);
        var local = environment(chunk);
        for (int offset : new int[] {-125_000_000, 125_000_000}) {
            var distant = new CameraEnvironment(new ChunkPos(offset, 0, offset), local.blocks());
            var eye = new PlayerState.Position((double) offset * 16 + 8.5, 8.5, (double) offset * 16 + 8.5);
            assertEquals(local.clip(EYE, 4, 0, 0, .2), distant.clip(eye, 4, 0, 0, .2));
            assertEquals(
                    local.medium(7.5, 8.1, 8.5),
                    distant.medium((double) offset * 16 + 7.5, 8.1, (double) offset * 16 + 8.5));
        }
    }

    private static double distance(Camera camera, PlayerState.Position eye) {
        return Math.sqrt(Math.pow(camera.x() - eye.x(), 2)
                + Math.pow(camera.y() - eye.y(), 2)
                + Math.pow(camera.z() - eye.z(), 2));
    }

    private static CameraEnvironment environment(Chunk chunk) {
        var world = new World();
        world.putChunk(CENTER, chunk);
        return new CameraEnvironment(CENTER, world.blockNeighborhood(CENTER));
    }
}
