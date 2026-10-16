package com.norbertfila.render;

import static com.norbertfila.render.ShaderPrograms.uniform;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.CloudPattern;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class CloudRenderer implements AutoCloseable {
    static final float HEIGHT = 192;
    static final float CELL_SIZE = 16;
    static final float RADIUS = 704;
    static final double SPEED = .6;
    private final int program;
    private final int vao;
    private final int texture;
    private final int viewProjection;
    private final int height;
    private final int phase;
    private final int periodUniform;
    private final double period;
    private boolean closed;

    public CloudRenderer() {
        CloudPattern pattern = CloudPattern.loadDefault();
        period = pattern.size() * (double) CELL_SIZE;
        int createdProgram = 0, createdVao = 0, createdTexture = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/clouds.vert", "/shaders/clouds.frag");
            viewProjection = uniform(createdProgram, "uVP");
            height = uniform(createdProgram, "uHeight");
            phase = uniform(createdProgram, "uPhase");
            periodUniform = uniform(createdProgram, "uPeriod");
            glProgramUniform1i(createdProgram, uniform(createdProgram, "uClouds"), 0);
            glProgramUniform1f(createdProgram, uniform(createdProgram, "uRadius"), RADIUS);
            createdVao = glGenVertexArrays();
            createdTexture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, createdTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
            byte[] alpha = pattern.alpha();
            var pixels = MemoryUtil.memAlloc(alpha.length);
            try {
                pixels.put(alpha).flip();
                int alignment = glGetInteger(GL_UNPACK_ALIGNMENT);
                try {
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexImage2D(
                            GL_TEXTURE_2D,
                            0,
                            GL_R8,
                            pattern.size(),
                            pattern.size(),
                            0,
                            GL_RED,
                            GL_UNSIGNED_BYTE,
                            pixels);
                } finally {
                    glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
                }
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(createdTexture);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        texture = createdTexture;
    }

    public void render(Matrix4f vp, double cameraX, double cameraY, double cameraZ, double seconds) {
        if (closed) throw new IllegalStateException("Cloud renderer is closed");
        if (!Double.isFinite(cameraX)
                || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ)
                || !Double.isFinite(seconds)
                || seconds < 0) throw new IllegalArgumentException("Invalid cloud view");
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUseProgram(program);
            glUniformMatrix4fv(viewProjection, false, vp.get(stack.mallocFloat(16)));
            glUniform1f(height, (float) (HEIGHT - cameraY));
            glUniform1f(periodUniform, (float) period);
            glUniform2f(phase, phase(cameraX - seconds * SPEED, period), phase(cameraZ, period));
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        } finally {
            glBindVertexArray(0);
            glBindTexture(GL_TEXTURE_2D, 0);
            glUseProgram(0);
            TerrainRenderer.restoreTerrainState();
        }
    }

    static float phase(double position, double period) {
        double remainder = position % period;
        return (float) (remainder < 0 ? remainder + period : remainder);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(texture);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
