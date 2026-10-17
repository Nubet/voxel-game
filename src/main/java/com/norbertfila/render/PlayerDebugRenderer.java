package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.player.visual.model.PlayerModel;
import com.norbertfila.player.visual.model.PlayerPose;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class PlayerDebugRenderer implements AutoCloseable {
    private final int program, vao, vbo, vp, model, min, size, color;
    private boolean closed;

    public PlayerDebugRenderer() {
        int createdProgram = 0, createdVao = 0, createdVbo = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/player_debug.vert", "/shaders/player_debug.frag");
            vp = ShaderPrograms.uniform(createdProgram, "uVP");
            model = ShaderPrograms.uniform(createdProgram, "uModel");
            min = ShaderPrograms.uniform(createdProgram, "uMin");
            size = ShaderPrograms.uniform(createdProgram, "uSize");
            color = ShaderPrograms.uniform(createdProgram, "uColor");
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

    public void render(Matrix4f viewProjection, Matrix4f modelToWorld, PlayerPose pose) {
        if (closed) throw new IllegalStateException("Player debug renderer is closed");
        Objects.requireNonNull(viewProjection);
        Objects.requireNonNull(modelToWorld);
        Objects.requireNonNull(pose);
        boolean depth = glIsEnabled(GL_DEPTH_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(true);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glUseProgram(program);
            glBindVertexArray(vao);
            var buffer = stack.mallocFloat(16);
            glUniformMatrix4fv(vp, false, viewProjection.get(buffer));
            var partMatrix = new Matrix4f();
            var worldMatrix = new Matrix4f();
            for (var part : PlayerModel.PARTS) {
                PlayerModel.partMatrix(part.part(), pose, partMatrix);
                modelToWorld.mul(partMatrix, worldMatrix);
                glUniformMatrix4fv(model, false, worldMatrix.get(buffer));
                var box = part.cuboid();
                glUniform3f(min, box.x(), box.y(), box.z());
                glUniform3f(size, box.width(), box.height(), box.depth());
                switch (part.part()) {
                    case HEAD -> glUniform3f(color, 1, .75f, .35f);
                    case BODY -> glUniform3f(color, .15f, .8f, .8f);
                    case RIGHT_ARM -> glUniform3f(color, .95f, .3f, .25f);
                    case LEFT_ARM -> glUniform3f(color, .35f, .85f, .3f);
                    case RIGHT_LEG -> glUniform3f(color, .3f, .4f, 1);
                    case LEFT_LEG -> glUniform3f(color, .8f, .3f, .9f);
                }
                glDrawArrays(GL_TRIANGLES, 0, 36);
            }
        } finally {
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            if (cull) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            if (blend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            glDepthMask(mask);
            glDepthFunc(depthFunction);
            if (depth) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }
    }

    private static float[] vertices() {
        return new float[] {
            0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0, 0,
            1, 0, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 0, 0, 1,
            0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0,
            1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 1,
            0, 1, 0, 0, 1, 1, 1, 1, 1, 0, 1, 0, 1, 1, 1, 1, 1, 0,
            0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 1
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
