package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.combat.CriticalHitEvent;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class CriticalHitRenderer implements AutoCloseable {
    private final SpriteBatch batch;
    private final int texture;
    private boolean closed;

    public CriticalHitRenderer() {
        var createdBatch = new SpriteBatch();
        try {
            texture = EnvironmentTexture.loadResource("/assets/textures/particles/critical_hit.png");
        } catch (RuntimeException | Error failure) {
            createdBatch.close();
            throw failure;
        }
        batch = createdBatch;
    }

    public void render(
            Matrix4f relativeViewProjection,
            List<CriticalHitEvent> events,
            double seconds,
            double cameraX,
            double cameraY,
            double cameraZ,
            Vector3fc viewDirection) {
        if (closed) throw new IllegalStateException("Critical hit renderer is closed");
        Objects.requireNonNull(relativeViewProjection);
        if (events.isEmpty()) return;
        var right = new Vector3f(viewDirection).cross(0, 1, 0);
        if (right.lengthSquared() < 1e-8) right.set(1, 0, 0);
        right.normalize();
        var up = new Vector3f(right).cross(viewDirection).normalize();
        var vertices = CriticalHitParticles.vertices(
                events, seconds, cameraX, cameraY, cameraZ, new float[] {right.x, right.y, right.z}, new float[] {
                    up.x, up.y, up.z
                });
        batch.draw(relativeViewProjection, vertices, texture, 0, 1);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(texture);
        batch.close();
    }
}
