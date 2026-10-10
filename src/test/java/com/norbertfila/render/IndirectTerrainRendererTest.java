package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.ChunkPos;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class IndirectTerrainRendererTest {
    @Test
    void emitsCommandsOnlyForVisibleMeshesWithStableOffsets() {
        Frustum frustum = Frustum.from(new Matrix4f());
        IndirectTerrainRenderer renderer = new IndirectTerrainRenderer();

        IndirectTerrainRenderer.Result result = renderer.build(
                List.of(
                        new IndirectTerrainRenderer.Batch(new ChunkPos(0, 0, 0), 12, 36),
                        new IndirectTerrainRenderer.Batch(new ChunkPos(20, 0, 0), 48, 24),
                        new IndirectTerrainRenderer.Batch(new ChunkPos(0, 0, 0), 100, 0)),
                frustum);

        assertEquals(1, result.visibleMeshes());
        assertEquals(1, result.drawCalls());
        assertEquals(12, result.commands().get(0).first());
        assertEquals(0, result.commands().get(0).baseInstance());
        assertTrue(result.buildNanos() >= 0);
    }
}
