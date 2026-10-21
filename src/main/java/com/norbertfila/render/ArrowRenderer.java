package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.projectile.ArrowRenderState;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;

/** Draws arrows as two crossed quads along their flight direction, like Minecraft's arrow model from the side. */
public final class ArrowRenderer implements AutoCloseable {
    static final double LENGTH = .9, WIDTH = .28;
    // The side view of the arrow occupies this pixel rectangle of the 64x64 texture, with the head on the right.
    private static final float U0 = 0, U1 = 30 / 64f, V0 = 1 / 64f, V1 = 9 / 64f;
    private final SpriteBatch batch;
    private final int texture;
    private boolean closed;

    public ArrowRenderer() {
        var createdBatch = new SpriteBatch();
        try {
            texture = EnvironmentTexture.loadResource("/assets/textures/entity/refi_arrow.png");
        } catch (RuntimeException | Error failure) {
            createdBatch.close();
            throw failure;
        }
        batch = createdBatch;
    }

    public void render(
            Matrix4f relativeViewProjection,
            List<ArrowRenderState> arrows,
            double alpha,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (closed) throw new IllegalStateException("Arrow renderer is closed");
        Objects.requireNonNull(relativeViewProjection);
        batch.draw(relativeViewProjection, vertices(arrows, alpha, cameraX, cameraY, cameraZ), texture, 0, 1);
    }

    static float[] vertices(
            List<ArrowRenderState> arrows, double alpha, double cameraX, double cameraY, double cameraZ) {
        var out = new float[arrows.size() * 12 * SpriteBatch.FLOATS_PER_VERTEX];
        int at = 0;
        for (var arrow : arrows) {
            double x = arrow.previousX() + (arrow.x() - arrow.previousX()) * alpha - cameraX;
            double y = arrow.previousY() + (arrow.y() - arrow.previousY()) * alpha - cameraY;
            double z = arrow.previousZ() + (arrow.z() - arrow.previousZ()) * alpha - cameraZ;
            double dx = arrow.directionX(), dy = arrow.directionY(), dz = arrow.directionZ();
            // First crossing vector: horizontal and perpendicular to the flight, or along X for a vertical arrow.
            double ax = -dz, ay = 0, az = dx, a = Math.hypot(ax, az);
            if (a < 1e-6) {
                ax = 1;
                az = 0;
            } else {
                ax /= a;
                az /= a;
            }
            double bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;
            for (double[] side : new double[][] {{ax, ay, az}, {bx, by, bz}}) {
                double sx = side[0] * WIDTH / 2, sy = side[1] * WIDTH / 2, sz = side[2] * WIDTH / 2;
                double hx = dx * LENGTH / 2, hy = dy * LENGTH / 2, hz = dz * LENGTH / 2;
                float[][] corners = {
                    {(float) (x - hx - sx), (float) (y - hy - sy), (float) (z - hz - sz), U0, V1},
                    {(float) (x + hx - sx), (float) (y + hy - sy), (float) (z + hz - sz), U1, V1},
                    {(float) (x + hx + sx), (float) (y + hy + sy), (float) (z + hz + sz), U1, V0},
                    {(float) (x - hx + sx), (float) (y - hy + sy), (float) (z - hz + sz), U0, V0}
                };
                for (int corner : new int[] {0, 1, 2, 0, 2, 3}) {
                    System.arraycopy(corners[corner], 0, out, at, SpriteBatch.FLOATS_PER_VERTEX);
                    at += SpriteBatch.FLOATS_PER_VERTEX;
                }
            }
        }
        return out;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(texture);
        batch.close();
    }
}
