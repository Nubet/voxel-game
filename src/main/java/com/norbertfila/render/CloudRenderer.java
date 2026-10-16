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
    private final int meshBuffer;
    private final int meshVertices;
    private final int viewProjection;
    private final int height;
    private final int phase;
    private final int periodUniform;
    private final int visibility;
    private final int fancy;
    private final double period;
    private boolean closed;

    public CloudRenderer() {
        CloudPattern pattern = CloudPattern.loadDefault();
        period = pattern.size() * (double) CELL_SIZE;
        int createdProgram = 0, createdVao = 0, createdTexture = 0, createdMesh = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/clouds.vert", "/shaders/clouds.frag");
            viewProjection = uniform(createdProgram, "uVP");
            height = uniform(createdProgram, "uHeight");
            phase = uniform(createdProgram, "uPhase");
            periodUniform = uniform(createdProgram, "uPeriod");
            visibility = uniform(createdProgram, "uVisibility");
            fancy = uniform(createdProgram, "uFancy");
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
            float[] mesh = CloudMesh.build(pattern, CELL_SIZE, 4);
            meshVertices = mesh.length / 4;
            createdMesh = glGenBuffers();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, createdMesh);
            glBufferData(GL_SHADER_STORAGE_BUFFER, mesh, GL_STATIC_DRAW);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        } catch (RuntimeException | Error failure) {
            glDeleteBuffers(createdMesh);
            glDeleteTextures(createdTexture);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        texture = createdTexture;
        meshBuffer = createdMesh;
    }

    public void render(Matrix4f vp, double cameraX, double cameraY, double cameraZ, double seconds, float underwater) {
        render(vp, cameraX, cameraY, cameraZ, seconds, underwater, CloudMode.FAST);
    }

    public void render(
            Matrix4f vp,
            double cameraX,
            double cameraY,
            double cameraZ,
            double seconds,
            float underwater,
            CloudMode mode) {
        java.util.Objects.requireNonNull(mode);
        if (closed) throw new IllegalStateException("Cloud renderer is closed");
        if (!Double.isFinite(cameraX)
                || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ)
                || !Double.isFinite(seconds)
                || seconds < 0) throw new IllegalArgumentException("Invalid cloud view");
        if (!Float.isFinite(underwater) || underwater < 0 || underwater > 1)
            throw new IllegalArgumentException("Underwater visibility must be in [0, 1]");
        if (underwater == 1 || mode == CloudMode.OFF) return;
        boolean extruded = mode == CloudMode.FANCY;
        glEnable(GL_DEPTH_TEST);
        // Dithered coverage lets thick clouds self-occlude without sorting translucent faces.
        glDepthMask(extruded);
        glDisable(GL_CULL_FACE);
        if (extruded) glDisable(GL_BLEND);
        else glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUseProgram(program);
            glUniformMatrix4fv(viewProjection, false, vp.get(stack.mallocFloat(16)));
            glUniform1f(height, (float) (HEIGHT - cameraY));
            glUniform1f(periodUniform, (float) period);
            glUniform2f(phase, movingPhase(cameraX, seconds, period), phase(cameraZ, period));
            glUniform1f(visibility, 1 - underwater);
            glUniform1i(fancy, extruded ? 1 : 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindVertexArray(vao);
            if (extruded) {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, meshBuffer);
                glDrawArraysInstanced(GL_TRIANGLES, 0, meshVertices, 9);
            } else glDrawArrays(GL_TRIANGLES, 0, 6);
        } finally {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, 0);
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

    static float movingPhase(double position, double seconds, double period) {
        return phase(phase(position, period) - (seconds % (period / SPEED)) * SPEED, period);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteBuffers(meshBuffer);
        glDeleteTextures(texture);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
