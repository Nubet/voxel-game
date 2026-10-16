package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.interaction.VoxelRaycaster;
import com.norbertfila.meshing.*;
import org.junit.jupiter.api.Test;

class PartialBlockTest {
    @Test
    void snowCoverSharesBoundsBetweenCollisionTargetingAndMesh() {
        World world = new World();
        for (int y = -1; y <= 1; y++)
            for (int z = -1; z <= 1; z++)
                for (int x = -1; x <= 1; x++) world.putChunk(new ChunkPos(x, y, z), new Chunk());
        var position = new BlockPos(0, 0, 0);
        world.setBlock(new BlockPos(0, -1, 0), BlockId.STONE, 64);
        world.setBlock(position, BlockId.SNOW_LAYER, 64);
        var collision = new VoxelCollision();
        var body = new Aabb(.2, 1, .2, .8, 2, .8);
        var result = collision.move(world, body, 0, -2, 0);
        assertEquals(.125, result.body().minY());
        assertTrue(collision.spaceFree(world, new Aabb(.2, .2, .2, .8, .9, .8)));
        var ray = new VoxelRaycaster();
        assertEquals(
                1.875, ray.cast(world, .5, 2, .5, 0, -1, 0, 4).orElseThrow().distance());
        assertTrue(ray.cast(world, -1, .5, .5, 1, 0, 0, 4).isEmpty());
        var neighborhood = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        float[] vertices = new GreedyMesher()
                .mesh(neighborhood.center(), neighborhood, 0, 0, 0)
                .vertices();
        float highest = 0;
        for (int i = 1; i < vertices.length; i += 6) highest = Math.max(highest, vertices[i]);
        assertEquals(.125f, highest);
    }
}
