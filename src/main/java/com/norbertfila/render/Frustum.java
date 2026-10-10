package com.norbertfila.render;

import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RegionPos;
import org.joml.Matrix4fc;

public final class Frustum {
    private final float[] planes = new float[24];

    private Frustum(Matrix4fc matrix) {
        setPlane(
                0,
                matrix.m03() + matrix.m00(),
                matrix.m13() + matrix.m10(),
                matrix.m23() + matrix.m20(),
                matrix.m33() + matrix.m30());
        setPlane(
                1,
                matrix.m03() - matrix.m00(),
                matrix.m13() - matrix.m10(),
                matrix.m23() - matrix.m20(),
                matrix.m33() - matrix.m30());
        setPlane(
                2,
                matrix.m03() + matrix.m01(),
                matrix.m13() + matrix.m11(),
                matrix.m23() + matrix.m21(),
                matrix.m33() + matrix.m31());
        setPlane(
                3,
                matrix.m03() - matrix.m01(),
                matrix.m13() - matrix.m11(),
                matrix.m23() - matrix.m21(),
                matrix.m33() - matrix.m31());
        setPlane(
                4,
                matrix.m03() + matrix.m02(),
                matrix.m13() + matrix.m12(),
                matrix.m23() + matrix.m22(),
                matrix.m33() + matrix.m32());
        setPlane(
                5,
                matrix.m03() - matrix.m02(),
                matrix.m13() - matrix.m12(),
                matrix.m23() - matrix.m22(),
                matrix.m33() - matrix.m32());
    }

    public static Frustum from(Matrix4fc matrix) {
        return new Frustum(matrix);
    }

    public boolean intersects(ChunkPos position) {
        float minX = position.x() * Chunk.SIZE;
        float minY = position.y() * Chunk.SIZE;
        float minZ = position.z() * Chunk.SIZE;
        return intersects(minX, minY, minZ, minX + Chunk.SIZE, minY + Chunk.SIZE, minZ + Chunk.SIZE);
    }

    public boolean intersects(RegionPos.Bounds bounds) {
        return intersects(
                bounds.minX() * Chunk.SIZE,
                bounds.minY() * Chunk.SIZE,
                bounds.minZ() * Chunk.SIZE,
                bounds.maxExclusiveX() * Chunk.SIZE,
                bounds.maxExclusiveY() * Chunk.SIZE,
                bounds.maxExclusiveZ() * Chunk.SIZE);
    }

    public boolean intersects(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int plane = 0; plane < 6; plane++) {
            int offset = plane * 4;
            float x = planes[offset] >= 0 ? maxX : minX;
            float y = planes[offset + 1] >= 0 ? maxY : minY;
            float z = planes[offset + 2] >= 0 ? maxZ : minZ;
            if (planes[offset] * x + planes[offset + 1] * y + planes[offset + 2] * z + planes[offset + 3] < 0) {
                return false;
            }
        }
        return true;
    }

    private void setPlane(int index, float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        int offset = index * 4;
        planes[offset] = a / length;
        planes[offset + 1] = b / length;
        planes[offset + 2] = c / length;
        planes[offset + 3] = d / length;
    }
}
