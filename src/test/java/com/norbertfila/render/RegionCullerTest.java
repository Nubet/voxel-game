package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.streaming.RegionIndex;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RegionCullerTest {
    @Test
    void matchesPerChunkCullingForManyCamerasNearAndFarFromTheOrigin() {
        Random random = new Random(3);
        for (int[] origin : new int[][] {{0, 0, 0}, {-37, -2, 51}, {4_000_000, 3, -6_000_000}}) {
            RegionIndex index = new RegionIndex(4);
            for (int x = -10; x <= 10; x++)
                for (int z = -10; z <= 10; z++)
                    for (int y = -2; y <= 2; y++)
                        if (random.nextInt(4) != 0)
                            index.add(new ChunkPos(origin[0] + x, origin[1] + y, origin[2] + z));
            for (int camera = 0; camera < 60; camera++) {
                double cx = (origin[0] + random.nextDouble(-6, 6)) * Chunk.SIZE;
                double cy = (origin[1] + random.nextDouble(-2, 2)) * Chunk.SIZE;
                double cz = (origin[2] + random.nextDouble(-6, 6)) * Chunk.SIZE;
                float yaw = random.nextFloat(0, 360), pitch = random.nextFloat(-89, 89);
                float fov = random.nextFloat(30, 110);
                Frustum frustum = Frustum.from(relativeProjection(yaw, pitch, fov));
                List<ChunkPos> visible = new ArrayList<>();
                RegionCuller culler = new RegionCuller();
                culler.cull(frustum, index, cx, cy, cz, visible);
                assertEquals(
                        new HashSet<>(RegionCuller.simpleVisible(frustum, index, cx, cy, cz)),
                        new HashSet<>(visible),
                        "camera " + camera + " near " + origin[0]);
                assertEquals(visible.size(), new HashSet<>(visible).size(), "duplicates");
            }
        }
    }

    @Test
    void rejectsRegionsBehindTheCameraWithoutTestingTheirChunks() {
        RegionIndex index = new RegionIndex(2);
        for (int x = -6; x <= 6; x++) index.add(new ChunkPos(x, 0, -3));
        for (int x = -6; x <= 6; x++) index.add(new ChunkPos(x, 0, 3));
        RegionCuller culler = new RegionCuller();
        List<ChunkPos> visible = new ArrayList<>();
        culler.cull(Frustum.from(relativeProjection(-90, 0, 70)), index, 8, 8, 8, visible);
        assertTrue(culler.rejectedRegions() >= 7, "regions behind the camera are rejected whole");
        assertTrue(visible.stream().allMatch(chunk -> chunk.z() < 0));
        assertTrue(culler.chunkTests() < 26);
    }

    @Test
    void visitsNearerRegionsFirst() {
        RegionIndex index = new RegionIndex(1);
        for (int z = -1; z >= -20; z--) index.add(new ChunkPos(0, 0, z));
        List<ChunkPos> visible = new ArrayList<>();
        new RegionCuller().cull(Frustum.from(relativeProjection(-90, 0, 70)), index, 8, 8, 0, visible);
        for (int i = 1; i < visible.size(); i++)
            assertTrue(visible.get(i).z() < visible.get(i - 1).z());
    }

    @Test
    void classifiesBoxesInsideIntersectingAndOutside() {
        Frustum frustum = Frustum.from(new Matrix4f());
        assertEquals(Frustum.INSIDE, frustum.classify(-.5f, -.5f, -.5f, .5f, .5f, .5f));
        assertEquals(Frustum.INTERSECTS, frustum.classify(.5f, .5f, .5f, 2, 2, 2));
        assertEquals(Frustum.OUTSIDE, frustum.classify(2, 2, 2, 3, 3, 3));
    }

    /** View projection looking along yaw and pitch from the origin, as the renderer uses it. */
    private static Matrix4f relativeProjection(float yaw, float pitch, float fov) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vector3f forward = new Vector3f(
                (float) (Math.cos(yr) * Math.cos(pr)), (float) Math.sin(pr), (float) (Math.sin(yr) * Math.cos(pr)));
        return new Matrix4f()
                .perspective((float) Math.toRadians(fov), 16 / 9f, .1f, 400)
                .lookAt(new Vector3f(), forward, new Vector3f(0, 1, 0));
    }
}
