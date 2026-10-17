package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class JungleModelTest {
    @Test
    void decodedDecorationVerticesStayInsideTheirSelectionBounds() {
        for (int id = 150; id <= 157; id++) {
            var world = new World();
            var chunk = new Chunk();
            chunk.set(3, 4, 5, (byte) id);
            var pos = new ChunkPos(0, 0, 0);
            world.putChunk(pos, chunk);
            var n = world.blockNeighborhood(pos);
            var mesh = new GreedyMesher().meshTextured(n.center(), n, 0, 0, 0);
            var model = BlockRegistry.DEFAULT.definition((byte) id).model();
            float[] vertices = mesh.vertices();
            assertTrue(vertices.length > 0);
            for (int i = 0; i < vertices.length; i += 7) {
                assertTrue(vertices[i] >= 3 + model.minX() && vertices[i] <= 3 + model.maxX());
                assertTrue(vertices[i + 1] >= 4 + model.minY() && vertices[i + 1] <= 4 + model.maxY());
                assertTrue(vertices[i + 2] >= 5 + model.minZ() && vertices[i + 2] <= 5 + model.maxZ());
            }
        }
    }
}
