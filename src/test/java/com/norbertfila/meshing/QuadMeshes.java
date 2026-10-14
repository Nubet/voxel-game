package com.norbertfila.meshing;

/** Expands quad meshes into explicit vertex meshes so tests can assert on triangles. */
public final class QuadMeshes {
    private QuadMeshes() {}

    public static MeshData solid(QuadMesh mesh, int cx, int cy, int cz) {
        QuadRanges ranges = mesh.ranges();
        int perQuad = QuadMesh.VERTICES_PER_QUAD;
        return MeshData.textured(
                QuadCodec.decodeTextured(mesh, cx, cy, cz),
                new MeshRanges(ranges.opaque() * perQuad, ranges.cutout() * perQuad, ranges.translucent() * perQuad));
    }

    public static MeshData water(QuadMesh mesh, int cx, int cy, int cz) {
        float[] vertices = QuadCodec.decodeTextured(mesh, cx, cy, cz);
        return new MeshData(
                MeshFormat.flowTextured(vertices),
                vertices,
                new MeshRanges(0, 0, mesh.quadCount() * QuadMesh.VERTICES_PER_QUAD));
    }
}
