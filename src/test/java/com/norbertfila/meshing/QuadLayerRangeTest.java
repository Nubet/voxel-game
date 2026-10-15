package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RenderLayer;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class QuadLayerRangeTest {
    @Test
    void everySolidQuadLiesInTheRangeOfItsBlockRenderLayer() {
        var generator = new TerrainGenerator(7_340_215L, TerrainConfig.defaults());
        World world = new World();
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                for (int y = 0; y <= 5; y++) {
                    ChunkPos position = new ChunkPos(x - 40, y, z + 11);
                    world.putChunk(position, generator.generate(position).toChunk());
                }
        int cutout = 0;
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int y = 0; y <= 5; y++) {
                    ChunkPos position = new ChunkPos(x - 40, y, z + 11);
                    var neighborhood = world.blockNeighborhood(position);
                    QuadMesh solid = new WaterMesher()
                            .mesh(
                                    neighborhood.center(),
                                    neighborhood,
                                    position.x(),
                                    position.y(),
                                    position.z(),
                                    ChunkMesh.ALL)
                            .solid();
                    if (solid == null) continue;
                    for (RenderLayer layer : new RenderLayer[] {RenderLayer.OPAQUE, RenderLayer.CUTOUT}) {
                        int first = solid.ranges().first(layer);
                        for (int quad = first; quad < first + solid.ranges().count(layer); quad++) {
                            byte block = (byte) (solid.records()[quad * 2 + 1] >>> 16);
                            assertEquals(
                                    layer,
                                    BlockRegistry.DEFAULT.definition(block).renderLayer(),
                                    position + " quad " + quad);
                            if (layer == RenderLayer.CUTOUT) cutout++;
                        }
                    }
                }
        assertTrue(cutout > 0);
    }
}
