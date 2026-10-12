package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.MeshRanges;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RenderLayer;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class IndirectTerrainRendererTest {
    @Test
    void selectsOnlyTheRequestedNonTranslucentRange() {
        ChunkPos position = new ChunkPos(0, 0, 0);
        MeshRanges ranges = new MeshRanges(6, 12, 18);
        var opaque = new IndirectTerrainRenderer.Batch(position, ranges, RenderLayer.OPAQUE);
        var cutout = new IndirectTerrainRenderer.Batch(position, ranges, RenderLayer.CUTOUT);
        assertEquals(0, opaque.firstVertex());
        assertEquals(6, opaque.vertexCount());
        assertEquals(6, cutout.firstVertex());
        assertEquals(12, cutout.vertexCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> new IndirectTerrainRenderer.Batch(position, ranges, RenderLayer.TRANSLUCENT));
        var empty = new IndirectTerrainRenderer.Batch(position, new MeshRanges(0, 12, 18), RenderLayer.OPAQUE);
        assertEquals(
                0,
                new IndirectTerrainRenderer()
                        .build(List.of(empty), Frustum.from(new Matrix4f()))
                        .visibleMeshes());
    }

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
