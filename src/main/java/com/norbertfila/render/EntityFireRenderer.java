package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.mob.MobRenderState;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Flames on burning mobs: stacked quads turned toward the camera, drawn at full brightness like fire in Minecraft. */
public final class EntityFireRenderer implements AutoCloseable {
    static final int FLOATS_PER_VERTEX = 5;
    private static final int FRAMES = 8;
    private static final double FRAMES_PER_SECOND = 16;
    // Minecraft widens the flames to 1.4 times the mob's width and stacks square sections up its body.
    private static final double WIDTH_SCALE = 1.4;
    private final int program, vao, vbo, texture, viewProjection, frame;
    private int capacity;
    private boolean closed;

    public EntityFireRenderer() {
        int createdProgram = 0, createdVao = 0, createdVbo = 0, createdTexture = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/entity_fire.vert", "/shaders/entity_fire.frag");
            viewProjection = ShaderPrograms.uniform(createdProgram, "uVP");
            frame = ShaderPrograms.uniform(createdProgram, "uFrame");
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uFrames"), FRAMES);
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uFire"), 0);
            createdTexture = EnvironmentTexture.loadResource("/assets/textures/entity/refi_fire_animated.png");
            createdVao = glCreateVertexArrays();
            createdVbo = glCreateBuffers();
            glVertexArrayVertexBuffer(createdVao, 0, createdVbo, 0, FLOATS_PER_VERTEX * Float.BYTES);
            glVertexArrayAttribFormat(createdVao, 0, 3, GL_FLOAT, false, 0);
            glVertexArrayAttribBinding(createdVao, 0, 0);
            glEnableVertexArrayAttrib(createdVao, 0);
            glVertexArrayAttribFormat(createdVao, 1, 2, GL_FLOAT, false, 3 * Float.BYTES);
            glVertexArrayAttribBinding(createdVao, 1, 0);
            glEnableVertexArrayAttrib(createdVao, 1);
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(createdTexture);
            glDeleteBuffers(createdVbo);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        vbo = createdVbo;
        texture = createdTexture;
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
        float[] vertices = vertices(mobs, alpha, cameraX, cameraY, cameraZ);
        if (vertices.length == 0) return;
        int bytes = vertices.length * Float.BYTES;
        if (bytes > capacity) {
            capacity = Integer.highestOneBit(bytes - 1) << 1;
            glNamedBufferData(vbo, capacity, GL_STREAM_DRAW);
        }
        var upload = MemoryUtil.memAllocFloat(vertices.length);
        try {
            glNamedBufferSubData(vbo, 0, upload.put(vertices).flip());
        } finally {
            MemoryUtil.memFree(upload);
        }
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST), depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glUseProgram(program);
            glUniformMatrix4fv(viewProjection, false, relativeViewProjection.get(stack.mallocFloat(16)));
            glUniform1i(frame, (int) Math.floorMod((long) Math.floor(seconds * FRAMES_PER_SECOND), FRAMES));
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, vertices.length / FLOATS_PER_VERTEX);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(previousActiveTexture);
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            if (cull) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            if (blend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            glDepthMask(depthMask);
            if (depthTest) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }
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
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
