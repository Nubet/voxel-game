package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.mob.MobRenderState;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;

/** Flames on burning mobs: stacked quads turned toward the camera, drawn at full brightness like fire in Minecraft. */
public final class EntityFireRenderer implements AutoCloseable {
    static final int FLOATS_PER_VERTEX = SpriteBatch.FLOATS_PER_VERTEX;
    private static final int FRAMES = 8;
    private static final double FRAMES_PER_SECOND = 16;
    // Minecraft widens the flames to 1.4 times the mob's width and stacks square sections up its body.
    private static final double WIDTH_SCALE = 1.4;
    private final SpriteBatch batch;
    private final int texture;
    private boolean closed;

    public EntityFireRenderer() {
        var createdBatch = new SpriteBatch();
        try {
            texture = EnvironmentTexture.loadResource("/assets/textures/entity/refi_fire_animated.png");
        } catch (RuntimeException | Error failure) {
            createdBatch.close();
            throw failure;
        }
        batch = createdBatch;
    }

    public void render(
            Matrix4f relativeViewProjection,
            List<MobRenderState> mobs,
            double alpha,
            double cameraX,
            double cameraY,
            double cameraZ,
            double seconds) {
        if (closed) throw new IllegalStateException("Entity fire renderer is closed");
        Objects.requireNonNull(relativeViewProjection);
        int frame = (int) Math.floorMod((long) Math.floor(seconds * FRAMES_PER_SECOND), FRAMES);
        batch.draw(relativeViewProjection, vertices(mobs, alpha, cameraX, cameraY, cameraZ), texture, frame, FRAMES);
    }

    /** Two triangles per flame section, camera-relative, with the quad's normal pointing at the camera. */
    static float[] vertices(List<MobRenderState> mobs, double alpha, double cameraX, double cameraY, double cameraZ) {
        int sections = 0;
        for (var mob : mobs) if (mob.burning()) sections += sections(mob);
        var out = new float[sections * 6 * FLOATS_PER_VERTEX];
        int at = 0;
        for (var mob : mobs) {
            if (!mob.burning()) continue;
            double x = mob.interpolatedX(alpha) - cameraX, z = mob.interpolatedZ(alpha) - cameraZ;
            double y = mob.interpolatedY(alpha) - cameraY;
            double size = mob.type().width() * WIDTH_SCALE;
            double distance = Math.hypot(x, z);
            // Right vector of a quad facing the camera; any direction works when the camera is straight above.
            double rightX = distance < 1e-6 ? 1 : z / distance, rightZ = distance < 1e-6 ? 0 : -x / distance;
            // Pull the flames in front of the body so that the mob's own faces do not hide them.
            double pull = distance < 1e-6 ? 0 : Math.min(mob.type().width() * .6, distance * .5) / distance;
            x -= x * pull;
            z -= z * pull;
            double halfX = rightX * size * .5, halfZ = rightZ * size * .5;
            for (int section = 0; section < sections(mob); section++) {
                double bottom = y + section * size, top = bottom + size;
                float[][] corners = {
                    {(float) (x - halfX), (float) bottom, (float) (z - halfZ), 0, 1},
                    {(float) (x + halfX), (float) bottom, (float) (z + halfZ), 1, 1},
                    {(float) (x + halfX), (float) top, (float) (z + halfZ), 1, 0},
                    {(float) (x - halfX), (float) top, (float) (z - halfZ), 0, 0}
                };
                for (int corner : new int[] {0, 1, 2, 0, 2, 3}) {
                    System.arraycopy(corners[corner], 0, out, at, FLOATS_PER_VERTEX);
                    at += FLOATS_PER_VERTEX;
                }
            }
        }
        return at == out.length ? out : Arrays.copyOf(out, at);
    }

    private static int sections(MobRenderState mob) {
        return (int) Math.ceil(mob.type().height() / (mob.type().width() * WIDTH_SCALE));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(texture);
        batch.close();
    }
}
