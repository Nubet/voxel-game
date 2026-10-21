package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Draws camera-relative textured triangles with alpha cutout and full brightness, for small effects such as flames
 * and arrows; vertices are XYZ plus UV, rebuilt every frame.
 */
final class SpriteBatch implements AutoCloseable {
    static final int FLOATS_PER_VERTEX = 5;
    private final int program, vao, vbo, viewProjection, frame, frames;
    private int capacity;
    private boolean closed;

    SpriteBatch() {
        int createdProgram = 0, createdVao = 0, createdVbo = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/entity_sprite.vert", "/shaders/entity_sprite.frag");
            viewProjection = ShaderPrograms.uniform(createdProgram, "uVP");
            frame = ShaderPrograms.uniform(createdProgram, "uFrame");
            frames = ShaderPrograms.uniform(createdProgram, "uFrames");
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uTexture"), 0);
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
            glDeleteBuffers(createdVbo);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        vbo = createdVbo;
    }

    /** Draws one frame of a vertical frame strip; single images pass frame 0 of 1. */
    void draw(Matrix4f relativeViewProjection, float[] vertices, int texture, int frameIndex, int frameCount) {
        if (closed) throw new IllegalStateException("Sprite batch is closed");
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
            glUniform1i(frame, frameIndex);
            glUniform1i(frames, frameCount);
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

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
