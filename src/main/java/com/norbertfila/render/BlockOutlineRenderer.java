package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.world.BlockPos;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class BlockOutlineRenderer implements AutoCloseable {
    private final int program, vao, vbo;
    private final int viewProjection, offset;
    private boolean closed;

    public BlockOutlineRenderer() {
        int createdProgram = 0, createdVao = 0, createdVbo = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/block_outline.vert", "/shaders/block_outline.frag");
            viewProjection = ShaderPrograms.uniform(createdProgram, "uVP");
            offset = ShaderPrograms.uniform(createdProgram, "uOffset");
            createdVao = glCreateVertexArrays();
            createdVbo = glCreateBuffers();
            glNamedBufferData(createdVbo, vertices(), GL_STATIC_DRAW);
            glVertexArrayVertexBuffer(createdVao, 0, createdVbo, 0, 3 * Float.BYTES);
            glVertexArrayAttribFormat(createdVao, 0, 3, GL_FLOAT, false, 0);
            glVertexArrayAttribBinding(createdVao, 0, 0);
            glEnableVertexArrayAttrib(createdVao, 0);
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

    public void render(
            Matrix4f relativeViewProjection, BlockPos block, double cameraX, double cameraY, double cameraZ) {
        if (closed) throw new IllegalStateException("Block outline renderer is closed");
        Objects.requireNonNull(relativeViewProjection);
        Objects.requireNonNull(block);
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ))
            throw new IllegalArgumentException("Camera position must be finite");
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST), depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        float lineWidth = glGetFloat(GL_LINE_WIDTH);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(false);
            glDisable(GL_BLEND);
            glLineWidth(1);
            glUseProgram(program);
            glUniformMatrix4fv(viewProjection, false, relativeViewProjection.get(stack.mallocFloat(16)));
            // Subtract in double precision before narrowing, including near the limits of block coordinates.
            glUniform3f(offset, (float) (block.x() - cameraX), (float) (block.y() - cameraY), (float)
                    (block.z() - cameraZ));
            glBindVertexArray(vao);
            glDrawArrays(GL_LINES, 0, 24);
        } finally {
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            glLineWidth(lineWidth);
            if (blend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            glDepthMask(depthMask);
            glDepthFunc(depthFunction);
            if (depthTest) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }
    }

    static float[] vertices() {
        float min = -.002f, max = 1.002f;
        return new float[] {
            min, min, min, max, min, min,
            min, max, min, max, max, min,
            min, min, max, max, min, max,
            min, max, max, max, max, max,
            min, min, min, min, max, min,
            max, min, min, max, max, min,
            min, min, max, min, max, max,
            max, min, max, max, max, max,
            min, min, min, min, min, max,
            max, min, min, max, min, max,
            min, max, min, min, max, max,
            max, max, min, max, max, max
        };
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
