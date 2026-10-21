package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.combat.CriticalHitEvent;
import com.norbertfila.mob.MobDeathEvent;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class ParticleRenderer implements AutoCloseable {
    private final SpriteBatch batch;
    private final int critical, smoke;
    private boolean closed;

    public ParticleRenderer() {
        var createdBatch = new SpriteBatch();
        int createdCritical = 0;
        try {
            createdCritical = EnvironmentTexture.loadResource("/assets/textures/particles/critical_hit.png");
            smoke = EnvironmentTexture.loadResource("/assets/textures/particles/refi_smoke.png");
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(createdCritical);
            createdBatch.close();
            throw failure;
        }
        critical = createdCritical;
        batch = createdBatch;
    }

    public void render(
            Matrix4f relativeViewProjection,
            List<CriticalHitEvent> criticalHits,
            List<MobDeathEvent> deaths,
            double seconds,
            double cameraX,
            double cameraY,
            double cameraZ,
            Vector3fc viewDirection) {
        if (closed) throw new IllegalStateException("Particle renderer is closed");
        Objects.requireNonNull(relativeViewProjection);
        if (criticalHits.isEmpty() && deaths.isEmpty()) return;
        var rightVector = new Vector3f(viewDirection).cross(0, 1, 0);
        if (rightVector.lengthSquared() < 1e-8) rightVector.set(1, 0, 0);
        rightVector.normalize();
        var upVector = new Vector3f(rightVector).cross(viewDirection).normalize();
        float[] right = {rightVector.x, rightVector.y, rightVector.z}, up = {upVector.x, upVector.y, upVector.z};
        batch.draw(
                relativeViewProjection,
                CriticalHitParticles.vertices(criticalHits, seconds, cameraX, cameraY, cameraZ, right, up),
                critical,
                0,
                1);
        var puffs = PoofParticles.vertices(deaths, seconds, cameraX, cameraY, cameraZ, right, up);
        for (int frame = 0; frame < puffs.length; frame++)
            batch.draw(relativeViewProjection, puffs[frame], smoke, frame, PoofParticles.FRAMES);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(critical);
        glDeleteTextures(smoke);
        batch.close();
    }
}
